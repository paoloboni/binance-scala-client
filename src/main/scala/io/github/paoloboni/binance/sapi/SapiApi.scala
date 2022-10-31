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

package io.github.paoloboni.binance.sapi

import cats.effect.Async
import cats.effect.implicits.effectResourceOps
import io.github.paoloboni.binance.common.SpotConfig
import io.github.paoloboni.binance.common.response.CirceResponse
import io.github.paoloboni.binance.{BinanceApi, spot}
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.ratelimit.RateLimiters
import sttp.client3.circe.asJson

final case class SapiApi[F[_]](
    config: SpotConfig,
    client: HttpClient[F],
    exchangeInfo: spot.response.ExchangeInformation,
    rateLimiters: RateLimiters[F]
)(implicit F: Async[F])
    extends BinanceApi[F] {

  override type Config = SpotConfig

  /** API V1
    */
  val V1: SpotRestApiV3Impl[F] = new SpotRestApiV3Impl[F](config, client, rateLimiters)
}

object SapiApi {
  implicit def factory[F[_]](implicit
      F: Async[F]
  ): BinanceApi.Factory[F, SapiApi[F], SpotConfig] =
    (config: SpotConfig, client: HttpClient[F]) =>
      for {
        exchangeInfoEither <- client
          .get[CirceResponse[spot.response.ExchangeInformation]](
            uri = config.exchangeInfoUrl,
            responseAs = asJson[spot.response.ExchangeInformation],
            limiters = List.empty
          )
          .toResource
        exchangeInfo <- F.fromEither(exchangeInfoEither).toResource
        rateLimiters <- exchangeInfo.createRateLimiters(config.rateLimiterBufferSize)
      } yield SapiApi(config, client, exchangeInfo, rateLimiters)
}
