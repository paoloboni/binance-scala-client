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
import io.github.paoloboni.WithClock
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.parameters.TimeParams
import io.github.paoloboni.binance.fapi.parameters._
import io.github.paoloboni.binance.fapi.response._
import io.github.paoloboni.binance.{BinanceApi, common, fapi}
import io.github.paoloboni.encryption.HMAC
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.QueryStringConverter.Ops
import io.github.paoloboni.http.ratelimit.RateLimiter
import io.lemonlabs.uri.Url
import log.effect.LogWriter
import org.http4s.EntityEncoder
import org.http4s.circe.CirceEntityDecoder._
import shapeless.tag

import java.time.Instant
import io.circe.Json

final case class FutureApi[F[_]: Async: WithClock: LogWriter](
    config: BinanceConfig,
    client: HttpClient[F],
    exchangeInfo: fapi.response.ExchangeInformation,
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
      path = "/fapi/v1/klines",
      query = query.toQueryString
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
      path = "/fapi/v1/ticker/price"
    )
    for {
      prices <- client.get[List[Price]](
        url = url,
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS),
        weight = 2
      )
    } yield prices
  }

  def getPrice(getPriceParams: PriceTickerParams): F[Price] = {
    val url = Url(
      scheme = config.scheme,
      host = config.host,
      port = config.port,
      path = "/fapi/v1/ticker/price"
    )

    for {
      price <- client.get[Price](
        url = url,
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS)
      )
    } yield price
  }

  def changePositionMode(changePosition: ChangePositionModeParams): F[Unit] = {
    def url(currentMillis: Long) = {
      val timeParams  = TimeParams(config.recvWindow, currentMillis).toQueryString
      val queryString = changePosition.toQueryString.addParams(timeParams)
      val signature   = HMAC.sha256(config.apiSecret, queryString.toString())
      Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/fapi/v1/positionSide/dual"
      ).withQueryString(queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- clock.realTime
      _ <- client
        .post[String, Json](
          url = url(currentTime.toMillis),
          requestBody = "",
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
    } yield ()

  }

  def changeInitialLeverage(changeLeverage: ChangeInitialLeverageParams): F[ChangeInitialLeverageResponse] = {

    def url(currentMillis: Long) = {
      val timeParams  = TimeParams(config.recvWindow, currentMillis).toQueryString
      val queryString = changeLeverage.toQueryString.addParams(timeParams)
      val signature   = HMAC.sha256(config.apiSecret, queryString.toString())
      Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/fapi/v1/leverage"
      ).withQueryString(queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- clock.realTime
      response <- client
        .post[String, ChangeInitialLeverageResponse](
          url = url(currentTime.toMillis),
          requestBody = "",
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
    } yield response
  }

  /** Returns the current balance, at the time the query is executed.
    *
    * @return The balance (free and locked) for each asset
    */
  def getBalance(): F[FutureAccountInfoResponse] = {
    def url(currentMillis: Long) = {
      val query       = TimeParams(config.recvWindow, currentMillis).toQueryString
      val signature   = HMAC.sha256(config.apiSecret, query.toString())
      val queryString = query.addParam("signature", signature)
      Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/fapi/v1/account",
        query = queryString
      )
    }
    for {
      currentTime <- clock.realTime
      balance <- client.get[FutureAccountInfoResponse](
        url = url(currentTime.toMillis),
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS),
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 5
      )
    } yield balance
  }

  private implicit val stringEncoder: EntityEncoder[F, String] = EntityEncoder.showEncoder

  /** Creates an order.
    *
    * @param orderCreate the parameters required to define the order
    *
    * @return The id of the order created
    */
  def createOrder(orderCreate: FutureOrderCreateParams): F[OrderId] = {

    def url(currentMillis: Long) = {
      val timeParams  = TimeParams(config.recvWindow, currentMillis).toQueryString
      val queryString = orderCreate.toQueryString.addParams(timeParams)
      val signature   = HMAC.sha256(config.apiSecret, queryString.toString())
      Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/fapi/v1/order"
      ).withQueryString(queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- clock.realTime
      orderId <- client
        .post[String, FutureOrderCreateResponse](
          url = url(currentTime.toMillis),
          requestBody = "",
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
        .map(response => tag[OrderIdTag][Long](response.orderId))
    } yield orderId
  }
}

object FutureApi {
  implicit def factory[F[_]: Async: WithClock: LogWriter]: BinanceApi.Factory[F, FutureApi[F]] =
    (config: BinanceConfig, client: HttpClient[F]) =>
      for {
        exchangeInfo <- client
          .get[fapi.response.ExchangeInformation](
            url = config.generateFullInfoUrl,
            limiters = List.empty
          )

        rateLimiters <- exchangeInfo.createRateLimiters(config.rateLimiterBufferSize)
      } yield FutureApi.apply(config, client, exchangeInfo, rateLimiters)
}
