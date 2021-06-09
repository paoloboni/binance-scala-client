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
import io.github.paoloboni.WithClock
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.spot.SpotApi
import io.github.paoloboni.http.HttpClient
import log.effect.LogWriter
import org.asynchttpclient.{AsyncHttpClientConfig, DefaultAsyncHttpClientConfig}
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

object BinanceClient {

  def createSpotClient[F[_]: WithClock: LogWriter: Async](config: SpotConfig)(implicit
      apiFactory: BinanceApi.Factory[F, spot.SpotApi[F], SpotConfig]
  ): Resource[F, SpotApi[F]] =
    apply[F, spot.SpotApi[F], SpotConfig](config)

  def createFutureClient[F[_]: WithClock: LogWriter: Async](config: FapiConfig)(implicit
      apiFactory: BinanceApi.Factory[F, fapi.FutureApi[F], FapiConfig]
  ): Resource[F, fapi.FutureApi[F]] =
    apply[F, fapi.FutureApi[F], FapiConfig](config)

  def apply[F[_]: WithClock: LogWriter: Async, API <: BinanceApi[F], Config <: BinanceConfig](
      config: Config
  )(implicit apiFactory: BinanceApi.Factory[F, API, Config]): Resource[F, API] = {
    val conf: AsyncHttpClientConfig =
      new DefaultAsyncHttpClientConfig.Builder()
        .setMaxConnections(config.maxTotalConnections)
        .setRequestTimeout(config.responseHeaderTimeout.toMillis.toInt)
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
