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
import io.github.paoloboni.binance.common.response.RateLimits
import io.github.paoloboni.binance.{BinanceApi, common}
import io.github.paoloboni.encryption.HMAC
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.ratelimit.RateLimiter
import io.lemonlabs.uri.{QueryString, Url}
import log.effect.LogWriter
import org.http4s.circe.CirceEntityDecoder._
import shapeless.tag

import java.time.Instant

final case class Api[F[_]: Async: WithClock: LogWriter](
    config: BinanceConfig,
    client: HttpClient[F],
    rateLimiters: List[RateLimiter[F]]
) extends BinanceApi[F] {

  private val clock = implicitly[WithClock[F]].clock

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint
    * in case the result set doesn't fit in a single page.
    *
    * @param query an `KLines` object containing the query parameters
    * @return the stream of Kline objects
    */
  def getKLines(query: common.parameters.KLines): Stream[F, KLine] = query match {
    case common.parameters.KLines(symbol, common.Interval(interval), startTime, endTime, limit) =>
      val url = Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/fapi/v1/klines",
        query = QueryString.fromPairs(
          "symbol"    -> symbol,
          "interval"  -> interval.toString,
          "startTime" -> startTime.toEpochMilli.toString,
          "endTime"   -> endTime.toEpochMilli.toString,
          "limit"     -> limit.toString
        )
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
          case loneElement :: Nil if (query.endTime.toEpochMilli - loneElement.openTime) > interval.duration.toMillis =>
            val newQuery = query.copy(startTime = Instant.ofEpochMilli(loneElement.closeTime))
            Stream.emit(loneElement) ++ getKLines(newQuery)

          case init :+ last if (query.endTime.toEpochMilli - last.openTime) > interval.duration.toMillis =>
            val newQuery = query.copy(startTime = Instant.ofEpochMilli(last.openTime))
            Stream.emits(init) ++ getKLines(newQuery)

          case list => Stream.emits(list)
        }
      } yield klines

    case other: common.parameters.KLines =>
      Stream.raiseError[F](
        new RuntimeException(s"${other.interval} is not a valid interval for Binance")
      )
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

  /** Returns the current balance, at the time the query is executed.
    *
    * @return The balance (free and locked) for each asset
    */
  def getBalance(): F[Map[Asset, Balance]] = {
    def url(currentMillis: Long) = {
      val query       = s"recvWindow=5000&timestamp=${currentMillis.toString}"
      val signature   = HMAC.sha256(config.apiSecret, query)
      val queryString = QueryString.parse(query).addParam("signature", signature)
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
      balances <- client.get[BinanceBalances](
        url = url(currentTime.toMillis),
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS),
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 5
      )
    } yield balances.balances.map(b => tag[AssetTag](b.asset) -> Balance(b.free, b.locked)).toMap
  }
}

object Api {
  implicit def factory[F[_]: Async: WithClock: LogWriter]: BinanceApi.Factory[F, Api[F]] =
    (config: BinanceConfig, client: HttpClient[F]) =>
      for {
        limits <- client
          .get[RateLimits](
            url = config.generateFullInfoUrl,
            limiters = List.empty
          )

        rateLimiters <- limits.rateLimits
          .map(_.toRate)
          .traverse(limit => RateLimiter.make[F](limit.perSecond, config.rateLimiterBufferSize, limit.limitType))

      } yield Api.apply(config, client, rateLimiters)
}
