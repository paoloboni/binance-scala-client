/*
 * Copyright (c) 2022 Paolo Boni
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

import cats.effect.kernel.Sync
import cats.effect.syntax.all._
import cats.effect.{Async, Resource}
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.spot.SpotApi
import io.github.paoloboni.http.HttpClient
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.httpclient.fs2.HttpClientFs2Backend

object BinanceClient {

  def defaultLogger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  def createSpotClient[F[_]: Async](config: SpotConfig)(implicit
      apiFactory: BinanceApi.Factory[F, spot.SpotApi[F], SpotConfig]
  ): Resource[F, SpotApi[F]] = createSpotClient(config, defaultLogger[F])

  def createSpotClient[F[_]: Async](config: SpotConfig, logger: Logger[F])(implicit
      apiFactory: BinanceApi.Factory[F, spot.SpotApi[F], SpotConfig]
  ): Resource[F, SpotApi[F]] =
    apply[F, spot.SpotApi[F], SpotConfig](config, logger)

  def createFutureClient[F[_]: Async](config: FapiConfig)(implicit
      apiFactory: BinanceApi.Factory[F, fapi.FutureApi[F], FapiConfig]
  ): Resource[F, fapi.FutureApi[F]] =
    createFutureClient(config, defaultLogger[F])

  def createFutureClient[F[_]: Async](config: FapiConfig, logger: Logger[F])(implicit
      apiFactory: BinanceApi.Factory[F, fapi.FutureApi[F], FapiConfig]
  ): Resource[F, fapi.FutureApi[F]] =
    apply[F, fapi.FutureApi[F], FapiConfig](config, logger)

  def apply[F[_]: Async, API <: BinanceApi.Aux[F, Config], Config <: BinanceConfig](
      config: Config,
      logger: Logger[F]
  )(implicit apiFactory: BinanceApi.Factory[F, API, Config]): Resource[F, API] = {
    implicit val log: Logger[F] = logger
    HttpClientFs2Backend
      .resource[F]()
      .flatMap { implicit backend =>
        for {
          client  <- HttpClient.make[F](config.responseHeaderTimeout).toResource
          spotApi <- BinanceApi.Factory[F, API, Config].apply(config, client)
        } yield spotApi
      }
  }
}
