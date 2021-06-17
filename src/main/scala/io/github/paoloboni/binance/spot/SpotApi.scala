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
import io.github.paoloboni.binance.common.response.{CirceResponse, KLineStream}
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.spot.response._
import io.github.paoloboni.binance.{BinanceApi, common, spot}
import io.github.paoloboni.encryption.HMAC
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.QueryStringConverter.Ops
import io.github.paoloboni.http.ratelimit.RateLimiter
import io.lemonlabs.uri.typesafe.dsl._
import log.effect.LogWriter
import sttp.client3.ResponseAsByteArray
import sttp.client3.circe._

import java.time.Instant

final case class SpotApi[F[_]: LogWriter](
    config: SpotConfig,
    client: HttpClient[F],
    exchangeInfo: spot.response.ExchangeInformation,
    rateLimiters: List[RateLimiter[F]]
)(implicit F: Async[F])
    extends BinanceApi[F] {

  type Config = SpotConfig

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint
    * in case the result set doesn't fit in a single page.
    *
    * @param query an `KLines` object containing the query parameters
    * @return the stream of Kline objects
    */
  def getKLines(query: common.parameters.KLines): Stream[F, KLine] = {
    val url = (config.restBaseUrl / "api/v3/klines").withQueryString(query.toQueryString)

    for {
      response <- Stream.eval(
        client.get[CirceResponse[List[KLine]]](
          url = url,
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
  }

  /** Returns a snapshot of the prices at the time the query is executed.
    *
    * @return A sequence of prices (one for each symbol)
    */
  def getPrices(): F[Seq[Price]] = {
    val url = config.restBaseUrl / "api/v3/ticker/price"
    for {
      pricesOrError <- client.get[CirceResponse[List[Price]]](
        url = url,
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
      val query       = TimeParams(config.recvWindow, currentMillis).toQueryString
      val signature   = HMAC.sha256(config.apiSecret, query.toString())
      val queryString = query.addParam("signature", signature)
      (config.restBaseUrl / "api/v3/account").withQueryString(queryString)
    }
    for {
      currentTime <- F.realTime
      responseOrError <- client.get[CirceResponse[SpotAccountInfoResponse]](
        url = url(currentTime.toMillis),
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
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryString
      val query = orderCreate.toQueryString
        .addParams(timeParams)
        .addParam("newOrderRespType" -> SpotOrderCreateResponseType.FULL.entryName)
      val signature   = HMAC.sha256(config.apiSecret, query.toString())
      val queryString = query.addParam("signature" -> signature)
      (config.restBaseUrl / "api/v3/order").withQueryString(queryString)
    }

    for {
      currentTime <- F.realTime
      responseOrError <- client
        .post[String, CirceResponse[SpotOrderCreateResponse]](
          url = url(currentTime.toMillis),
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

    def urlAndBody(currentMillis: Long) = {
      val timeParams  = TimeParams(config.recvWindow, currentMillis).toQueryString
      val queryString = orderCancel.toQueryString.addParams(timeParams)
      val signature   = HMAC.sha256(config.apiSecret, queryString.toString())
      val url         = config.restBaseUrl / "api/v3/order"
      (url, queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- F.realTime
      (url, requestBody) = urlAndBody(currentTime.toMillis)
      _ <- client
        .delete[String, CirceResponse[io.circe.Json]](
          url = url,
          responseAs = asJson[io.circe.Json],
          requestBody = Some(requestBody.toString()),
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
    } yield ()
  }

  /** Cancels all orders of a symbol.
    *
    * @param orderCancel the parameters required to cancel all the orders
    *
    * @return currently nothing
    */
  def cancelAllOrders(orderCancel: SpotOrderCancelAllParams): F[Unit] = {

    def urlAndBody(currentMillis: Long) = {
      val timeParams  = TimeParams(config.recvWindow, currentMillis).toQueryString
      val queryString = orderCancel.toQueryString.addParams(timeParams)
      val signature   = HMAC.sha256(config.apiSecret, queryString.toString())
      val url         = config.restBaseUrl / "api/v3/openOrders"
      (url, queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- F.realTime
      (url, requestBody) = urlAndBody(currentTime.toMillis)
      _ <- client
        .delete[String, Array[Byte]](
          url = url,
          responseAs = ResponseAsByteArray,
          requestBody = Some(requestBody.toString()),
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
    } yield ()
  }

  /** The Kline/Candlestick Stream push updates to the current klines/candlestick every second.
    *
    * @param symbol the symbol
    * @param interval the interval
    * @return a stream of klines
    */
  def kLineStreams(symbol: String, interval: Interval): Stream[F, KLineStream] =
    client.ws[KLineStream](config.wsBaseUrl / s"ws/${symbol.toLowerCase}@kline_${interval.entryName}")
}

object SpotApi {
  implicit def factory[F[_]: LogWriter](implicit
      F: Async[F]
  ): BinanceApi.Factory[F, SpotApi[F]] =
    (config: SpotConfig, client: HttpClient[F]) =>
      for {
        exchangeInfoEither <- client
          .get[CirceResponse[spot.response.ExchangeInformation]](
            url = config.exchangeInfoUrl,
            responseAs = asJson[spot.response.ExchangeInformation],
            limiters = List.empty
          )
        exchangeInfo <- F.fromEither(exchangeInfoEither)
        rateLimiters <- exchangeInfo.createRateLimiters(config.rateLimiterBufferSize)
      } yield SpotApi.apply(config, client, exchangeInfo, rateLimiters)
}
