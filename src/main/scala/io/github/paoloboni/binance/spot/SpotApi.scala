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
import io.github.paoloboni.WithClock
import io.github.paoloboni.binance.common.BinanceConfig.RecvWindow
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.parameters.{KLines, TimeParams}
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.spot.response._
import io.github.paoloboni.binance.{BinanceApi, common, spot}
import io.github.paoloboni.encryption.HMAC
import io.github.paoloboni.http.ratelimit.RateLimiter
import io.github.paoloboni.http.{HttpClient, QueryStringConverter}
import io.lemonlabs.uri.Url
import log.effect.LogWriter
import org.http4s.EntityEncoder
import org.http4s.circe.CirceEntityDecoder._
import shapeless.tag

import java.time.Instant

final case class SpotApi[F[_]: Async: WithClock: LogWriter](
    config: BinanceConfig,
    client: HttpClient[F],
    exchangeInfo: spot.response.ExchangeInformation,
    rateLimiters: List[RateLimiter[F]]
) extends BinanceApi[F] {

  private val clock = implicitly[WithClock[F]].clock

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint
    * in case the result set doesn't fit in a single page.
    *
    * @param query an `KLines` object containing the query parameters
    * @return the stream of Kline objects
    */
  def getKLines(query: common.parameters.KLines): Stream[F, KLine] = {
    val url = Url(
      scheme = config.scheme,
      host = config.host,
      port = config.port,
      path = "/api/v3/klines",
      query = QueryStringConverter[KLines].to(query)
    )

    for {
      rawKlines <- Stream.eval(
        client.get[List[KLine]](
          url,
          limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS)
        )
      )
      klines <- rawKlines match {
        //check if a lone element is enough to fullfill the query. Otherwise a limit of 1 leads
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
    val url = Url(
      scheme = config.scheme,
      host = config.host,
      port = config.port,
      path = "/api/v3/ticker/price"
    )
    for {
      prices <- client.get[List[Price]](
        url = url,
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS),
        weight = 2
      )
    } yield prices
  }

  /** Returns the current balance, at the time the query is executed.
    *
    * @return The balance (free and locked) for each asset
    */
  def getBalance(): F[Map[Asset, Balance]] = {
    def url(currentMillis: Long) = {
      val query       = QueryStringConverter[TimeParams].to(TimeParams(config.recvWindow, currentMillis))
      val signature   = HMAC.sha256(config.apiSecret, query.toString())
      val queryString = query.addParam("signature", signature)
      Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/api/v3/account",
        query = queryString
      )
    }
    for {
      currentTime <- clock.realTime
      balances <- client.get[BinanceBalances](
        url = url(currentTime.toMillis),
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS),
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 5
      )
    } yield balances.balances.map(b => tag[AssetTag](b.asset) -> Balance(b.free, b.locked)).toMap
  }

  private implicit val stringEncoder: EntityEncoder[F, String] = EntityEncoder.showEncoder

  /** Creates an order.
    *
    * @param orderCreate the parameters required to define the order
    *
    * @return The id of the order created
    */
  def createOrder(orderCreate: SpotOrderCreateParams): F[OrderId] = {

    def urlAndBody(currentMillis: Long) = {
      val timeParams = QueryStringConverter[TimeParams].to(TimeParams(config.recvWindow, currentMillis))
      val queryString = QueryStringConverter[SpotOrderCreateParams]
        .to(orderCreate)
        .addParams(timeParams)
      val signature = HMAC.sha256(config.apiSecret, queryString.toString())
      val url = Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/api/v3/order"
      )
      (url, queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- clock.realTime
      (url, requestBody) = urlAndBody(currentTime.toMillis)
      orderId <- client
        .post[String, SpotOrderCreateResponse](
          url = url,
          requestBody = requestBody.toString(),
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
        .map(response => tag[OrderIdTag][Long](response.orderId))
    } yield orderId
  }

  /** Cancels an order.
    *
    * @param orderCancel the parameters required to cancel the order
    *
    * @return currently nothing
    */
  def cancelOrder(orderCancel: SpotOrderCancelParams): F[Unit] = {

    def urlAndBody(currentMillis: Long) = {
      val timeParams = QueryStringConverter[TimeParams].to(TimeParams(config.recvWindow, currentMillis))
      val queryString = QueryStringConverter[SpotOrderCancelParams]
        .to(orderCancel)
        .addParams(timeParams)
      val signature = HMAC.sha256(config.apiSecret, queryString.toString())
      val url = Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/api/v3/order"
      )
      (url, queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- clock.realTime
      (url, requestBody) = urlAndBody(currentTime.toMillis)
      _ <- client
        .delete[String, io.circe.Json](
          url = url,
          requestBody = requestBody.toString(),
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
      val timeParams = QueryStringConverter[TimeParams].to(TimeParams(config.recvWindow, currentMillis))
      val queryString = QueryStringConverter[SpotOrderCancelAllParams]
        .to(orderCancel)
        .addParams(timeParams)
      val signature = HMAC.sha256(config.apiSecret, queryString.toString())
      val url = Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/api/v3/openOrders"
      )
      (url, queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- clock.realTime
      (url, requestBody) = urlAndBody(currentTime.toMillis)
      _ <- client
        .delete[String, io.circe.Json](
          url = url,
          requestBody = requestBody.toString(),
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
    } yield ()
  }
}

object SpotApi {
  implicit def factory[F[_]: Async: WithClock: LogWriter]: BinanceApi.Factory[F, SpotApi[F]] =
    (config: BinanceConfig, client: HttpClient[F]) =>
      for {
        exchangeInfo <- client
          .get[spot.response.ExchangeInformation](
            url = config.generateFullInfoUrl,
            limiters = List.empty
          )

        rateLimiters <- exchangeInfo.createRateLimiters(config.rateLimiterBufferSize)
      } yield SpotApi.apply(config, client, exchangeInfo, rateLimiters)
}
