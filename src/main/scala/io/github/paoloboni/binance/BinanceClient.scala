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
import io.github.paoloboni.WithClock
import io.github.paoloboni.binance.common.response.RateLimitInterval._
import io.github.paoloboni.binance.common.response.{RateLimitType, RateLimits}
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.spot.Api
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.ratelimit.{Rate, RateLimiter}
import io.lemonlabs.uri.Url
import log.effect.LogWriter
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object BinanceClient {

  def createSpotClient[F[_]: WithClock: LogWriter: Async](config: BinanceConfig): Resource[F, Api[F]] =
    apply[F, spot.Api[F]](config)

  def createFutureClient[F[_]: WithClock: LogWriter: Async](config: BinanceConfig): Resource[F, fapi.Api[F]] =
    apply[F, fapi.Api[F]](config)

  def apply[F[_]: WithClock: LogWriter: Async, API <: BinanceApi[F]](
      config: BinanceConfig
  )(implicit apiFactory: BinanceApi.Factory[F, API]): Resource[F, API] = {

    BlazeClientBuilder[F](global)
      .withResponseHeaderTimeout(config.responseHeaderTimeout)
      .withMaxTotalConnections(config.maxTotalConnections)
      .resource
      .evalMap { implicit c =>
        def requestRateLimits(client: HttpClient[F]) = for {
          rateLimits <- client.get[RateLimits](
            url = config.generateFullInfoUrl,
            limiters = List.empty
          )
        } yield rateLimits.rateLimits
          .map(_.toRate)

        for {
          client <- HttpClient.make[F]
          limits <- requestRateLimits(client)
          limiters <- limits
            .map(limit => RateLimiter.make[F](limit.perSecond, config.rateLimiterBufferSize, limit.limitType))
            .sequence
          spotApi = BinanceApi.Factory[F, API].apply(config, client, limiters)
        } yield spotApi
      }
  }
}
