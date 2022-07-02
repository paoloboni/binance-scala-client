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
import org.asynchttpclient.{AsyncHttpClientConfig, DefaultAsyncHttpClientConfig}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

object BinanceClient {

  def defaultLogger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  def createSpotClient[F[_]: Async](config: SpotConfig[F])(implicit
      apiFactory: BinanceApi.Factory[F, spot.SpotApi[F], SpotConfig[F]]
  ): Resource[F, SpotApi[F]] = createSpotClient(config, defaultLogger[F])

  def createSpotClient[F[_]: Async](config: SpotConfig[F], logger: Logger[F])(implicit
      apiFactory: BinanceApi.Factory[F, spot.SpotApi[F], SpotConfig[F]]
  ): Resource[F, SpotApi[F]] =
    apply[F, spot.SpotApi[F], SpotConfig[F]](config, logger)

  def createFutureClient[F[_]: Async](config: FapiConfig[F])(implicit
      apiFactory: BinanceApi.Factory[F, fapi.FutureApi[F], FapiConfig[F]]
  ): Resource[F, fapi.FutureApi[F]] =
    createFutureClient(config, defaultLogger[F])

  def createFutureClient[F[_]: Async](config: FapiConfig[F], logger: Logger[F])(implicit
      apiFactory: BinanceApi.Factory[F, fapi.FutureApi[F], FapiConfig[F]]
  ): Resource[F, fapi.FutureApi[F]] =
    apply[F, fapi.FutureApi[F], FapiConfig[F]](config, logger)

  def apply[F[_]: Async, API <: BinanceApi[F], Config <: BinanceConfig.Aux[F, API]](
      config: Config,
      logger: Logger[F]
  )(implicit apiFactory: BinanceApi.Factory[F, API, Config]): Resource[F, API] = {
    val conf: AsyncHttpClientConfig =
      new DefaultAsyncHttpClientConfig.Builder()
        .setMaxConnections(config.maxTotalConnections)
        .setRequestTimeout(config.responseHeaderTimeout.toMillis.toInt)
        .setWebSocketMaxFrameSize(61440)
        .build()
    implicit val log: Logger[F] = logger
    AsyncHttpClientFs2Backend
      .resourceUsingConfig(conf)
      .flatMap { implicit c =>
        for {
          client  <- HttpClient.make[F].toResource
          spotApi <- BinanceApi.Factory[F, API, Config].apply(config, client)
        } yield spotApi
      }
  }
}
