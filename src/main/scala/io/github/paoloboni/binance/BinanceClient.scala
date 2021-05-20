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

package io.github.paoloboni.binance

import cats.effect.{Async, Resource}
import cats.implicits._
import io.circe.generic.auto._
import io.github.paoloboni.{WithClock, binance}
import io.github.paoloboni.binance.common.RateLimitInterval._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.ratelimit.{Rate, RateLimiter}
import io.lemonlabs.uri.Url
import log.effect.LogWriter
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

sealed class BinanceClient[F[_]: WithClock: Async: LogWriter] private (
    spotApi: binance.spot.Api[F]
) {

  lazy val spot: binance.spot.Api[F] = spotApi
}

object BinanceClient {

  def apply[F[_]: WithClock: LogWriter: Async](
      config: BinanceConfig
  ): Resource[F, BinanceClient[F]] = {

    BlazeClientBuilder[F](global)
      .withResponseHeaderTimeout(config.responseHeaderTimeout)
      .withMaxTotalConnections(config.maxTotalConnections)
      .resource
      .evalMap { implicit c =>
        def requestRateLimits(client: HttpClient[F]) = for {
          rateLimits <- client.get[RateLimits](
            Url(
              scheme = config.scheme,
              host = config.host,
              port = config.port,
              path = config.infoUrl
            ),
            limiters = List.empty
          )
          requestLimits = rateLimits.rateLimits
            .map(limit =>
              Rate(
                limit.limit,
                limit.interval match {
                  case SECOND => limit.intervalNum.seconds
                  case MINUTE => limit.intervalNum.minutes
                  case DAY    => limit.intervalNum.days
                },
                limit.rateLimitType
              )
            )
        } yield requestLimits

        for {
          client <- HttpClient.make[F]
          limits <- requestRateLimits(client)
          limiters <- limits
            .map(limit => RateLimiter.make[F](limit.perSecond, config.rateLimiterBufferSize, limit.limitType))
            .sequence
          spotApi = new binance.spot.Api[F](config, client, limiters)
        } yield new BinanceClient(spotApi)
      }
  }
}
