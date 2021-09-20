/*
 * Copyright (c) 2021 Paolo Boni
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.paoloboni.binance.fapi

import cats.effect.Async
import cats.implicits._
import fs2.Stream
import io.circe.generic.auto._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.parameters.TimeParams
import io.github.paoloboni.binance.common.response.{CirceResponse, ContractKLineStream, KLineStream}
import io.github.paoloboni.binance.fapi.parameters._
import io.github.paoloboni.binance.fapi.response._
import io.github.paoloboni.binance.{BinanceApi, common, fapi}
import io.github.paoloboni.encryption.HMAC
import io.github.paoloboni.http.QueryParamsConverter._
import io.github.paoloboni.http.ratelimit.RateLimiters
import io.github.paoloboni.http.{HttpClient, UriOps}
import org.typelevel.log4cats.Logger
import sttp.client3.circe.{asJson, circeBodySerializer}
import sttp.client3.{ResponseAsByteArray, UriContext}
import sttp.model.QueryParams

import java.time.Instant
import scala.util.Try

final case class FutureApi[F[_]: Logger](
    config: FapiConfig[F],
    client: HttpClient[F],
    exchangeInfo: fapi.response.ExchangeInformation,
    rateLimiters: RateLimiters[F]
)(implicit F: Async[F])
    extends BinanceApi[F] {

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint in case the result set doesn't
    * fit in a single page.
    *
    * @param query
    *   an `KLines` object containing the query parameters
    * @return
    *   the stream of Kline objects
    */
  def getKLines(query: common.parameters.KLines): Stream[F, KLine] = {
    val params: QueryParams = query.toQueryParams

    for {
      uri <- Stream.eval(F.fromEither(Try(uri"${config.restBaseUrl}/fapi/v1/klines").map(_.addParams(params)).toEither))
      response <- Stream.eval(
        client.get[CirceResponse[List[KLine]]](
          uri = uri,
          responseAs = asJson[List[KLine]],
          limiters = rateLimiters.requestsOnly
        )
      )
      rawKlines <- Stream.eval(F.fromEither(response))
      klines <- rawKlines match {
        //check if a lone element is enough to fulfill the query. Otherwise a limit of 1 leads
        //to a strange behaviour
        case loneElement :: Nil
            if (query.endTime.toEpochMilli - loneElement.openTime) > query.interval.duration.toMillis =>
          val newQuery = query.copy(startTime = Instant.ofEpochMilli(loneElement.closeTime))
          Stream.emit(loneElement) ++ getKLines(newQuery)

        case init :+ last if (query.endTime.toEpochMilli - last.openTime) > query.interval.duration.toMillis =>
          val newQuery = query.copy(startTime = Instant.ofEpochMilli(last.openTime))
          Stream.emits(init) ++ getKLines(newQuery)

        case list => Stream.emits(list)
      }
    } yield klines
  }

  /** Returns a snapshot of the prices at the time the query is executed.
    *
    * @return
    *   A sequence of prices (one for each symbol)
    */
  def getPrices(): F[Seq[Price]] =
    for {
      uri <- F.fromEither(Try(uri"${config.restBaseUrl}/fapi/v1/ticker/price").toEither)
      pricesOrError <- client.get[CirceResponse[List[Price]]](
        uri = uri,
        responseAs = asJson[List[Price]],
        limiters = rateLimiters.requestsOnly,
        weight = 2
      )
      prices <- F.fromEither(pricesOrError)
    } yield prices

  /** Returns the latest price for a symbol.
    *
    * @param symbol
    *   The symbol
    * @return
    *   The price for the symbol
    */
  def getPrice(symbol: String): F[Price] =
    for {
      uri <- F.fromEither(
        Try(uri"${config.restBaseUrl}/fapi/v1/ticker/price")
          .map(_.addParams(QueryParams.fromMap(Map("symbol" -> symbol))))
          .toEither
      )
      res <- client
        .get[CirceResponse[Price]](
          uri = uri,
          responseAs = asJson[Price],
          limiters = rateLimiters.requestsOnly
        )
      price <- F.fromEither(res)
    } yield price

  /** Change user's position mode (Hedge Mode or One-way Mode ) on EVERY symbol
    *
    * @param dualSidePosition
    *   "true": Hedge Mode; "false": One-way Mode
    * @return
    *   Unit
    */
  def changePositionMode(dualSidePosition: Boolean): F[Unit] = {
    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryParams
      val query      = timeParams.param("dualSidePosition", dualSidePosition.toString)
      for {
        uri <- Try(uri"${config.restBaseUrl}/fapi/v1/positionSide/dual")
          .map(_.addParams(query))
          .toEither
        signature = HMAC.sha256(config.apiSecret, uri.queryString)
      } yield uri.addParam("signature", signature)
    }

    for {
      currentTime <- F.realTime
      uri         <- F.fromEither(url(currentTime.toMillis))
      _ <- client
        .post[String, Array[Byte]](
          uri = uri,
          responseAs = ResponseAsByteArray,
          requestBody = None,
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
    } yield ()

  }

  /** Change user's initial leverage of specific symbol market.
    *
    * @param changeLeverage
    *   request parameters
    * @return
    *   the new leverage
    */
  def changeInitialLeverage(changeLeverage: ChangeInitialLeverageParams): F[ChangeInitialLeverageResponse] = {

    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryParams
      val query      = changeLeverage.toQueryParams.param(timeParams.toMap)
      for {
        uri <- Try(uri"${config.restBaseUrl}/fapi/v1/leverage")
          .map(_.addParams(query))
          .toEither
        signature = HMAC.sha256(config.apiSecret, uri.queryString)
      } yield uri.addParam("signature", signature)
    }

    for {
      currentTime <- F.realTime
      uri         <- F.fromEither(url(currentTime.toMillis))
      responseOrError <- client
        .post[String, CirceResponse[ChangeInitialLeverageResponse]](
          uri = uri,
          responseAs = asJson[ChangeInitialLeverageResponse],
          requestBody = None,
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      response <- F.fromEither(responseOrError)
    } yield response
  }

  /** Returns the current balance, at the time the query is executed.
    *
    * @return
    *   The balance (free and locked) for each asset
    */
  def getBalance(): F[FutureAccountInfoResponse] = {
    def url(currentMillis: Long) = {
      val query = TimeParams(config.recvWindow, currentMillis).toQueryParams
      for {
        uri <- Try(uri"${config.restBaseUrl}/fapi/v1/account")
          .map(_.addParams(query))
          .toEither
        signature = HMAC.sha256(config.apiSecret, uri.queryString)
      } yield uri.addParam("signature", signature)

    }
    for {
      currentTime <- F.realTime
      uri         <- F.fromEither(url(currentTime.toMillis))
      balanceOrError <- client.get[CirceResponse[FutureAccountInfoResponse]](
        uri = uri,
        responseAs = asJson[FutureAccountInfoResponse],
        limiters = rateLimiters.requestsOnly,
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 5
      )
      balance <- F.fromEither(balanceOrError)
    } yield balance
  }

  /** Creates an order.
    *
    * @param orderCreate
    *   the parameters required to define the order
    *
    * @return
    *   The id of the order created
    */
  def createOrder(orderCreate: FutureOrderCreateParams): F[FutureOrderCreateResponse] = {
    val params = orderCreate.toQueryParams

    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryParams
      val query = params
        .param(timeParams.toMap)
        .param("newOrderRespType", FutureOrderCreateResponseType.RESULT.toString)
      for {
        uri <- Try(uri"${config.restBaseUrl}/fapi/v1/order")
          .map(_.addParams(query))
          .toEither
        signature = HMAC.sha256(config.apiSecret, uri.queryString)
      } yield uri.addParam("signature", signature)
    }

    for {
      currentTime <- F.realTime
      uri         <- F.fromEither(url(currentTime.toMillis))
      responseOrError <- client
        .post[String, CirceResponse[FutureOrderCreateResponse]](
          uri = uri,
          responseAs = asJson[FutureOrderCreateResponse],
          requestBody = None,
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      response <- F.fromEither(responseOrError)
    } yield response
  }

  /** Gets order data.
    *
    * @param getOrder
    *   the parameters required to find existing order
    *
    * @return
    *   The id of the order created
    */
  def getOrder(getOrder: FutureGetOrderParams): F[FutureOrderGetResponse] = {
    val params = getOrder.toQueryParams

    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryParams
      val query      = params.param(timeParams.toMap)
      for {
        uri <- Try(uri"${config.restBaseUrl}/fapi/v1/order")
          .map(_.addParams(query))
          .toEither
        signature = HMAC.sha256(config.apiSecret, uri.queryString)
      } yield uri.addParam("signature", signature)
    }

    for {
      currentTime <- F.realTime
      uri         <- F.fromEither(url(currentTime.toMillis))
      responseOrError <- client
        .get[CirceResponse[FutureOrderGetResponse]](
          uri = uri,
          responseAs = asJson[FutureOrderGetResponse],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      response <- F.fromEither(responseOrError)
    } yield response
  }

  /** Cancels an order.
    *
    * @param orderCancel
    *   the parameters required to cancel the order
    *
    * @return
    *   currently nothing
    */
  def cancelOrder(orderCancel: FutureOrderCancelParams): F[Unit] = {

    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryParams
      val query      = orderCancel.toQueryParams.param(timeParams.toMap)
      for {
        uri <- Try(uri"${config.restBaseUrl}/fapi/v1/order").map(_.addParams(query)).toEither
        signature = HMAC.sha256(config.apiSecret, uri.queryString)
      } yield uri.addParam("signature", signature)
    }

    for {
      currentTime <- F.realTime
      uri         <- F.fromEither(url(currentTime.toMillis))
      res <- client
        .delete[CirceResponse[io.circe.Json]](
          uri = uri,
          responseAs = asJson[io.circe.Json],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      _ <- F.fromEither(res)
    } yield ()
  }

  /** Cancels all orders of a symbol.
    *
    * @param orderCancel
    *   the parameters required to cancel all the orders
    *
    * @return
    *   currently nothing
    */
  def cancelAllOrders(orderCancel: FutureOrderCancelAllParams): F[Unit] = {

    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryParams
      val query      = orderCancel.toQueryParams.param(timeParams.toMap)
      for {
        uri <- Try(uri"${config.restBaseUrl}/fapi/v1/allOpenOrders")
          .map(_.addParams(query))
          .toEither
        signature = HMAC.sha256(config.apiSecret, uri.queryString)
      } yield uri.addParam("signature", signature)
    }

    for {
      currentTime <- F.realTime
      uri         <- F.fromEither(url(currentTime.toMillis))
      res <- client
        .delete[CirceResponse[io.circe.Json]](
          uri = uri,
          responseAs = asJson[io.circe.Json],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      _ <- F.fromEither(res)
    } yield ()
  }

  /** The Aggregate Trade Streams push trade information that is aggregated for a single taker order every 100
    * milliseconds.
    *
    * @param symbol
    *   the symbol
    * @return
    *   a stream of aggregate trade events
    */
  def aggregateTradeStreams(symbol: String): Stream[F, AggregateTradeStream] =
    for {
      uri    <- Stream.eval(F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@aggTrade").toEither))
      stream <- client.ws[AggregateTradeStream](uri)
    } yield stream

  /** The Kline/Candlestick Stream push updates to the current klines/candlestick every 250 milliseconds (if existing).
    *
    * @param symbol
    *   the symbol
    * @param interval
    *   the interval
    * @return
    *   a stream of klines
    */
  def kLineStreams(symbol: String, interval: Interval): Stream[F, KLineStream] =
    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@kline_${interval.toString}").toEither)
      )
      stream <- client.ws[KLineStream](uri)
    } yield stream

  /** Continuous Contract Kline/Candlestick Streams of updates every 250 milliseconds.
    *
    * @param symbol
    *   the symbol
    * @param contractType
    *   the contract type
    * @param interval
    *   the interval
    * @return
    *   a stream of contract klines
    */
  def contractKLineStreams(
      symbol: String,
      contractType: FutureContractType,
      interval: Interval
  ): Stream[F, ContractKLineStream] =
    for {
      uri <- Stream.eval(
        F.fromEither(
          Try(
            uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}_${contractType.toString.toLowerCase}@continuousKline_${interval.toString}"
          ).toEither
        )
      )
      stream <- client.ws[ContractKLineStream](uri)
    } yield stream

  /** Mark price and funding rate for a single symbol pushed every 3 seconds
    *
    * @param symbol
    *   the symbol
    * @return
    *   a stream of mark price updates
    */
  def markPriceStream(symbol: String): Stream[F, MarkPriceUpdate] =
    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@markPrice").toEither)
      )
      stream <- client.ws[MarkPriceUpdate](uri)
    } yield stream

  /** Mark price and funding rate for all symbols pushed every 3 seconds
    *
    * @return
    *   a stream of mark price updates
    */
  def markPriceStream(): Stream[F, MarkPriceUpdate] =
    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.wsBaseUrl}/ws/!markPrice@arr").toEither)
      )
      stream <- client.ws[List[MarkPriceUpdate]](uri).flatMap(Stream.emits(_))
    } yield stream
}

object FutureApi {
  implicit def factory[F[_]: Logger](implicit
      F: Async[F]
  ): BinanceApi.Factory[F, FutureApi[F], FapiConfig[F]] =
    (config: FapiConfig[F], client: HttpClient[F]) =>
      for {
        exchangeInfoEither <- client
          .get[CirceResponse[fapi.response.ExchangeInformation]](
            uri = config.exchangeInfoUrl,
            responseAs = asJson[fapi.response.ExchangeInformation],
            limiters = List.empty
          )
        exchangeInfo <- F.fromEither(exchangeInfoEither)
        rateLimiters <- exchangeInfo.createRateLimiters(config.rateLimiterBufferSize)
      } yield FutureApi.apply(config, client, exchangeInfo, rateLimiters)
}
