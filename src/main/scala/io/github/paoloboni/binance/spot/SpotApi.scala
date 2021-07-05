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

package io.github.paoloboni.binance.spot

import cats.effect.Async
import cats.implicits._
import fs2.Stream
import io.circe.generic.auto._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.parameters.TimeParams
import io.github.paoloboni.binance.common.response._
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.spot.response._
import io.github.paoloboni.binance.{BinanceApi, common, spot}
import io.github.paoloboni.encryption.HMAC
import io.github.paoloboni.http.QueryParamsConverter._
import io.github.paoloboni.http.ratelimit.RateLimiter
import io.github.paoloboni.http.{HttpClient, UriOps}
import org.typelevel.log4cats.Logger
import sttp.client3.UriContext
import sttp.client3.circe.{asJson, _}

import java.time.Instant
import scala.util.Try

final case class SpotApi[F[_]: Logger](
    config: SpotConfig[F],
    client: HttpClient[F],
    exchangeInfo: spot.response.ExchangeInformation,
    rateLimiters: List[RateLimiter[F]]
)(implicit F: Async[F])
    extends BinanceApi[F] {

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint
    * in case the result set doesn't fit in a single page.
    *
    * @param query an `KLines` object containing the query parameters
    * @return the stream of Kline objects
    */
  def getKLines(query: common.parameters.KLines): Stream[F, KLine] =
    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.restBaseUrl}/api/v3/klines").map(_.addParams(query.toQueryParams)).toEither)
      )
      response <- Stream.eval(
        client.get[CirceResponse[List[KLine]]](
          uri = uri,
          responseAs = asJson[List[KLine]],
          limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS)
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

  /** Returns a snapshot of the prices at the time the query is executed.
    *
    * @return A sequence of prices (one for each symbol)
    */
  def getPrices(): F[Seq[Price]] = {
    for {
      uri <- F.fromEither(Try(uri"${config.restBaseUrl}/api/v3/ticker/price").toEither)
      pricesOrError <- client.get[CirceResponse[List[Price]]](
        uri = uri,
        responseAs = asJson[List[Price]],
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS),
        weight = 2
      )
      prices <- F.fromEither(pricesOrError)
    } yield prices
  }

  /** Returns the current balance, at the time the query is executed.
    *
    * @return The account information including the balance (free and locked) for each asset
    */
  def getBalance(): F[SpotAccountInfoResponse] = {
    def url(currentMillis: Long) = {
      val query = TimeParams(config.recvWindow, currentMillis).toQueryParams
      for {
        uri <- Try(uri"${config.restBaseUrl}/api/v3/account").map(_.addParams(query)).toEither
        signature = HMAC.sha256(config.apiSecret, uri.queryString)
      } yield uri.addParam("signature", signature)
    }
    for {
      currentTime <- F.realTime
      uri         <- F.fromEither(url(currentTime.toMillis))
      responseOrError <- client.get[CirceResponse[SpotAccountInfoResponse]](
        uri = uri,
        responseAs = asJson[SpotAccountInfoResponse],
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS),
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 10
      )
      response <- F.fromEither(responseOrError)
    } yield response
  }

  /** Creates an order.
    *
    * @param orderCreate the parameters required to define the order
    *
    * @return The id of the order created
    */
  def createOrder(orderCreate: SpotOrderCreateParams): F[SpotOrderCreateResponse] = {

    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryParams
      val query = orderCreate.toQueryParams
        .param(timeParams.toMap)
        .param("newOrderRespType", SpotOrderCreateResponseType.FULL.toString)
      for {
        uri <- Try(uri"${config.restBaseUrl}/api/v3/order").map(_.addParams(query)).toEither
        signature = HMAC.sha256(config.apiSecret, uri.queryString)
      } yield uri.addParam("signature", signature)
    }

    for {
      currentTime <- F.realTime
      uri         <- F.fromEither(url(currentTime.toMillis))
      responseOrError <- client
        .post[String, CirceResponse[SpotOrderCreateResponse]](
          uri = uri,
          responseAs = asJson[SpotOrderCreateResponse],
          requestBody = None,
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      response <- F.fromEither(responseOrError)
    } yield response
  }

  /** Cancels an order.
    *
    * @param orderCancel the parameters required to cancel the order
    *
    * @return currently nothing
    */
  def cancelOrder(orderCancel: SpotOrderCancelParams): F[Unit] = {

    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryParams
      val query      = orderCancel.toQueryParams.param(timeParams.toMap)
      for {
        uri <- Try(uri"${config.restBaseUrl}/api/v3/order").map(_.addParams(query)).toEither
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
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      _ <- F.fromEither(res)
    } yield ()
  }

  /** Cancels all orders of a symbol.
    *
    * @param orderCancel the parameters required to cancel all the orders
    *
    * @return currently nothing
    */
  def cancelAllOrders(orderCancel: SpotOrderCancelAllParams): F[Unit] = {

    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryParams
      val query      = orderCancel.toQueryParams.param(timeParams.toMap)
      for {
        uri <- Try(uri"${config.restBaseUrl}/api/v3/openOrders")
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
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      _ <- F.fromEither(res)
    } yield ()
  }

  /** The Kline/Candlestick Stream push updates to the current klines/candlestick every second.
    *
    * @param symbol the symbol
    * @param interval the interval
    * @return a stream of klines
    */
  def kLineStreams(symbol: String, interval: Interval): Stream[F, KLineStream] =
    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@kline_${interval.toString}").toEither)
      )
      stream <- client.ws[KLineStream](uri)
    } yield stream

  /** Order book price and quantity depth updates used to locally manage an order book.
    *
    *  @param symbol the symbol
    * @return a stream of order book price and quantity depth updates
    */
  def diffDepthStream(symbol: String): Stream[F, DiffDepthStream] =
    for {
      uri    <- Stream.eval(F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@depth").toEither))
      stream <- client.ws[DiffDepthStream](uri)
    } yield stream

  /** Top bids and asks
    *
    * @param symbol the symbol
    * @param level the level
    * @return a stream of top bids and asks
    */
  def partialBookDepthStream(symbol: String, level: Level): Stream[F, PartialDepthStream] =
    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@depth${level.toString}").toEither)
      )
      stream <- client.ws[PartialDepthStream](uri)
    } yield stream

  /** Pushes any update to the best bid or ask's price or quantity in real-time for all symbols.
    *
    * @return a stream of best bid or ask's price or quantity for all symbols
    */
  def allBookTickersStream(): Stream[F, BookTicker] =
    for {
      uri    <- Stream.eval(F.fromEither(Try(uri"${config.wsBaseUrl}/ws/!bookTicker").toEither))
      stream <- client.ws[BookTicker](uri)
    } yield stream
}

object SpotApi {
  implicit def factory[F[_]: Logger](implicit
      F: Async[F]
  ): BinanceApi.Factory[F, SpotApi[F], SpotConfig[F]] =
    (config: SpotConfig[F], client: HttpClient[F]) =>
      for {
        exchangeInfoEither <- client
          .get[CirceResponse[spot.response.ExchangeInformation]](
            uri = config.exchangeInfoUrl,
            responseAs = asJson[spot.response.ExchangeInformation],
            limiters = List.empty
          )
        exchangeInfo <- F.fromEither(exchangeInfoEither)
        rateLimiters <- exchangeInfo.createRateLimiters(config.rateLimiterBufferSize)
      } yield SpotApi.apply(config, client, exchangeInfo, rateLimiters)
}
