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

import cats.effect.{Async, Resource}
import cats.syntax.all._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.spot.SpotApi
import io.github.paoloboni.http.HttpClient
import org.asynchttpclient.{AsyncHttpClientConfig, DefaultAsyncHttpClientConfig}
import org.typelevel.log4cats.Logger
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

object BinanceClient {

  def createSpotClient[F[_]: Logger: Async](config: SpotConfig[F])(implicit
      apiFactory: BinanceApi.Factory[F, spot.SpotApi[F], SpotConfig[F]]
  ): Resource[F, SpotApi[F]] =
    apply[F, spot.SpotApi[F], SpotConfig[F]](config)

  def createFutureClient[F[_]: Logger: Async](config: FapiConfig[F])(implicit
      apiFactory: BinanceApi.Factory[F, fapi.FutureApi[F], FapiConfig[F]]
  ): Resource[F, fapi.FutureApi[F]] =
    apply[F, fapi.FutureApi[F], FapiConfig[F]](config)

  def apply[F[_]: Logger: Async, API <: BinanceApi[F], Config <: BinanceConfig.Aux[F, API]](
      config: Config
  )(implicit apiFactory: BinanceApi.Factory[F, API, Config]): Resource[F, API] = {
    val conf: AsyncHttpClientConfig =
      new DefaultAsyncHttpClientConfig.Builder()
        .setMaxConnections(config.maxTotalConnections)
        .setRequestTimeout(config.responseHeaderTimeout.toMillis.toInt)
        .setWebSocketMaxFrameSize(61440)
        .build()
    AsyncHttpClientFs2Backend
      .resourceUsingConfig(conf)
      .evalMap { implicit c =>
        for {
          client  <- HttpClient.make[F]
          spotApi <- BinanceApi.Factory[F, API, Config].apply(config, client)
        } yield spotApi
      }
  }
}
