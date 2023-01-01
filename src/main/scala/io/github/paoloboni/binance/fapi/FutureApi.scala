/*
 * Copyright (c) 2023 Paolo Boni
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
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.Stream
import io.circe.generic.auto._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.response.{CirceResponse, ContractKLineStream, KLineStream, Level}
import io.github.paoloboni.binance.fapi.parameters._
import io.github.paoloboni.binance.fapi.response._
import io.github.paoloboni.binance.{BinanceApi, common, fapi}
import io.github.paoloboni.encryption.MkSignedUri
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.QueryParamsConverter._
import io.github.paoloboni.http.ratelimit.RateLimiters
import sttp.client3.circe.{asJson, circeBodySerializer}
import sttp.client3.{ResponseAsByteArray, UriContext}
import sttp.model.QueryParams

import java.time.Instant
import scala.util.Try

final case class FutureApi[F[_]](
    config: FapiConfig,
    client: HttpClient[F],
    exchangeInfo: fapi.response.ExchangeInformation,
    rateLimiters: RateLimiters[F]
)(implicit F: Async[F])
    extends BinanceApi[F] {

  type Config = FapiConfig

  private val mkSignedUri = new MkSignedUri[F](
    recvWindow = config.recvWindow,
    apiSecret = config.apiSecret
  )

  private val baseUrl             = config.restBaseUrl.addPath("fapi", "v1")
  private val depthUri            = baseUrl.addPath("depth")
  private val kLinesUri           = baseUrl.addPath("klines")
  private val tickerPriceUri      = baseUrl.addPath("ticker", "price")
  private val positionSideDualUri = baseUrl.addPath("positionSide", "dual")
  private val leverageUri         = baseUrl.addPath("leverage")
  private val accountUri          = baseUrl.addPath("account")
  private val orderUri            = baseUrl.addPath("order")
  private val openOrdersUri       = baseUrl.addPath("openOrders")
  private val allOpenOrdersUri    = baseUrl.addPath("allOpenOrders")

  /** Returns the depth of the orderbook.
    *
    * @param query
    *   an `Depth` object containing the query parameters
    * @return
    *   the orderbook depth
    */
  def getDepth(query: DepthParams): F[DepthGetResponse] = {
    val params = query.toQueryParams
    val uri    = depthUri.addParams(params)

    for {
      depthOrError <- client.get[CirceResponse[DepthGetResponse]](
        uri = uri,
        responseAs = asJson[DepthGetResponse],
        limiters = rateLimiters.requestsOnly,
        weight = query.limit.weight
      )
      depth <- F.fromEither(depthOrError)
    } yield depth
  }

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint in case the result set doesn't
    * fit in a single page.
    *
    * @param query
    *   an `KLines` object containing the query parameters
    * @return
    *   the stream of Kline objects
    */
  def getKLines(query: common.parameters.KLines): Stream[F, KLine] = {
    val params = query.toQueryParams
    val uri    = kLinesUri.addParams(params)

    for {
      response <- Stream.eval(
        client.get[CirceResponse[List[KLine]]](
          uri = uri,
          responseAs = asJson[List[KLine]],
          limiters = rateLimiters.requestsOnly
        )
      )
      rawKlines <- Stream.eval(F.fromEither(response))
      klines <- rawKlines match {
        // check if a lone element is enough to fulfill the query. Otherwise a limit of 1 leads
        // to a strange behaviour
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
      pricesOrError <- client.get[CirceResponse[List[Price]]](
        uri = tickerPriceUri,
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
  def getPrice(symbol: String): F[Price] = {
    val uri = tickerPriceUri.addParams(QueryParams.fromMap(Map("symbol" -> symbol)))
    for {
      res <- client
        .get[CirceResponse[Price]](
          uri = uri,
          responseAs = asJson[Price],
          limiters = rateLimiters.requestsOnly
        )
      price <- F.fromEither(res)
    } yield price
  }

  /** Change user's position mode (Hedge Mode or One-way Mode) on EVERY symbol
    *
    * @param dualSidePosition
    *   "true": Hedge Mode; "false": One-way Mode
    * @return
    *   Unit
    */
  def changePositionMode(dualSidePosition: Boolean): F[Unit] =
    for {
      uri <- mkSignedUri(
        uri = positionSideDualUri,
        params = "dualSidePosition" -> dualSidePosition.toString
      )
      _ <- client
        .post[String, Array[Byte]](
          uri = uri,
          responseAs = ResponseAsByteArray,
          requestBody = None,
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
    } yield ()

  /** Change user's initial leverage of specific symbol market.
    *
    * @param changeLeverage
    *   request parameters
    * @return
    *   the new leverage
    */
  def changeInitialLeverage(changeLeverage: ChangeInitialLeverageParams): F[ChangeInitialLeverageResponse] =
    for {
      uri <- mkSignedUri(
        uri = leverageUri,
        params = changeLeverage.toQueryParams.toSeq: _*
      )
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

  /** Returns the current balance, at the time the query is executed.
    *
    * @return
    *   The balance (free and locked) for each asset
    */
  def getBalance(): F[FutureAccountInfoResponse] =
    for {
      uri <- mkSignedUri(uri = accountUri)
      balanceOrError <- client.get[CirceResponse[FutureAccountInfoResponse]](
        uri = uri,
        responseAs = asJson[FutureAccountInfoResponse],
        limiters = rateLimiters.requestsOnly,
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 5
      )
      balance <- F.fromEither(balanceOrError)
    } yield balance

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
    for {
      uri <- mkSignedUri(
        uri = orderUri,
        params = params.param("newOrderRespType", FutureOrderCreateResponseType.RESULT.toString).toSeq: _*
      )
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
  def getOrder(getOrder: FutureGetOrderParams): F[FutureOrderGetResponse] =
    for {
      uri <- mkSignedUri(
        uri = orderUri,
        params = getOrder.toQueryParams.toSeq: _*
      )
      responseOrError <- client
        .get[CirceResponse[FutureOrderGetResponse]](
          uri = uri,
          responseAs = asJson[FutureOrderGetResponse],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      response <- F.fromEither(responseOrError)
    } yield response

  /** Get all open orders on a symbol.
    *
    * @param symbol
    *   the symbol
    *
    * @return
    *   The list of open orders for the selected symbol
    */
  def getAllOpenOrders(symbol: String): F[List[FutureOrderGetResponse]] =
    for {
      uri <- mkSignedUri(
        uri = openOrdersUri,
        params = "symbol" -> symbol
      )
      responseOrError <- client
        .get[CirceResponse[List[FutureOrderGetResponse]]](
          uri = uri,
          responseAs = asJson[List[FutureOrderGetResponse]],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      response <- F.fromEither(responseOrError)
    } yield response

  /** Get all open orders
    *
    * @return
    *   The list of open orders for all symbols
    */
  def getAllOpenOrders(): F[List[FutureOrderGetResponse]] =
    for {
      uri <- mkSignedUri(uri = openOrdersUri)
      responseOrError <- client
        .get[CirceResponse[List[FutureOrderGetResponse]]](
          uri = uri,
          responseAs = asJson[List[FutureOrderGetResponse]],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey),
          weight = 40
        )
      response <- F.fromEither(responseOrError)
    } yield response

  /** Cancels an order.
    *
    * @param orderCancel
    *   the parameters required to cancel the order
    *
    * @return
    *   currently nothing
    */
  def cancelOrder(orderCancel: FutureOrderCancelParams): F[Unit] =
    for {
      uri <- mkSignedUri(
        uri = orderUri,
        params = orderCancel.toQueryParams.toSeq: _*
      )
      res <- client
        .delete[CirceResponse[io.circe.Json]](
          uri = uri,
          responseAs = asJson[io.circe.Json],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      _ <- F.fromEither(res)
    } yield ()

  /** Cancels all orders of a symbol.
    *
    * @param orderCancel
    *   the parameters required to cancel all the orders
    *
    * @return
    *   currently nothing
    */
  def cancelAllOrders(orderCancel: FutureOrderCancelAllParams): F[Unit] =
    for {
      uri <- mkSignedUri(
        uri = allOpenOrdersUri,
        params = orderCancel.toQueryParams.toSeq: _*
      )
      res <- client
        .delete[CirceResponse[io.circe.Json]](
          uri = uri,
          responseAs = asJson[io.circe.Json],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      _ <- F.fromEither(res)
    } yield ()

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

  /** Order book price and quantity depth updates used to locally manage an order book.
    *
    * @param symbol
    *   the symbol
    * @param depthUpdateSpeed
    *   the depth update speed
    * @return
    *   a stream of order book price and quantity depth updates
    */
  def diffDepthStream(symbol: String, depthUpdateSpeed: DepthUpdateSpeed): Stream[F, DiffDepthStream] = {
    val updateSpeed = depthUpdateSpeed match {
      case DepthUpdateSpeed.`100ms` => "@100ms"
      case DepthUpdateSpeed.`250ms` => ""
      case DepthUpdateSpeed.`500ms` => "@500ms"
    }

    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@depth${updateSpeed}").toEither)
      )
      stream <- client.ws[DiffDepthStream](uri)
    } yield stream
  }

  /** Top bids and asks
    *
    * @param symbol
    *   the symbol
    * @param level
    *   the level
    * @param depthUpdateSpeed
    *   the depth update speed
    * @return
    *   a stream of top bids and asks
    */
  def partialBookDepthStream(
      symbol: String,
      level: Level,
      depthUpdateSpeed: DepthUpdateSpeed
  ): Stream[F, PartialDepthStream] = {
    val updateSpeed = depthUpdateSpeed match {
      case DepthUpdateSpeed.`100ms` => "@100ms"
      case DepthUpdateSpeed.`250ms` => ""
      case DepthUpdateSpeed.`500ms` => "@500ms"
    }

    for {
      uri <- Stream.eval(
        F.fromEither(
          Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@depth${level.toString}${updateSpeed}").toEither
        )
      )
      stream <- client.ws[PartialDepthStream](uri)
    } yield stream
  }
}

object FutureApi {
  implicit def factory[F[_]](implicit
      F: Async[F]
  ): BinanceApi.Factory[F, FutureApi[F], FapiConfig] =
    (config: FapiConfig, client: HttpClient[F]) =>
      for {
        exchangeInfoEither <- client
          .get[CirceResponse[fapi.response.ExchangeInformation]](
            uri = config.exchangeInfoUrl,
            responseAs = asJson[fapi.response.ExchangeInformation],
            limiters = List.empty
          )
          .toResource
        exchangeInfo <- F.fromEither(exchangeInfoEither).toResource
        rateLimiters <- exchangeInfo.createRateLimiters(config.rateLimiterBufferSize)
      } yield FutureApi.apply(config, client, exchangeInfo, rateLimiters)
}
