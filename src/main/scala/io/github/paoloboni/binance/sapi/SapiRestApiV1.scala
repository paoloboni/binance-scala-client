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
import io.github.paoloboni.binance.BinanceApi
import io.github.paoloboni.binance.common.SpotConfig
import io.github.paoloboni.binance.sapi.parameters.MarginAccountNewOrderParams
import io.github.paoloboni.binance.sapi.response.MarginAccountNewOrderResponse
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.ratelimit.RateLimiters

trait MarginRestApiV1[F[_]] extends BinanceApi[F] {
  override type Config = SpotConfig

  /** Creates an order.
    *
    * @param orderCreate
    *   the parameters required to define the order
    * @return
    *   A set of attributes for related to the new order
    */
  def createOrder(orderCreate: MarginAccountNewOrderParams): F[MarginAccountNewOrderResponse]
}

class SpotRestApiV3Impl[F[_]](
    override val config: SpotConfig,
    client: HttpClient[F],
    rateLimiters: RateLimiters[F]
)(implicit F: Async[F])
    extends MarginRestApiV1[F] {

  import cats.syntax.all._
  import io.circe.generic.auto._
  import io.github.paoloboni.binance.common.response._
  import io.github.paoloboni.binance.spot.parameters._
  import io.github.paoloboni.encryption.MkSignedUri
  import io.github.paoloboni.http.QueryParamsConverter._
  import sttp.client3.circe.{asJson, _}

  private val mkSignedUri = new MkSignedUri[F](
    recvWindow = config.recvWindow,
    apiSecret = config.apiSecret
  )

  private val baseUrl  = config.restBaseUrl.addPath("sapi", "v1", "margin")
  private val orderUri = baseUrl.addPath("order")

  override def createOrder(orderCreate: MarginAccountNewOrderParams): F[MarginAccountNewOrderResponse] = {

    val params = orderCreate.toQueryParams.param("newOrderRespType", SpotOrderCreateResponseType.FULL.toString).toSeq
    for {
      uri <- mkSignedUri(
        uri = orderUri,
        params: _*
      )
      responseOrError <- client
        .post[String, CirceResponse[MarginAccountNewOrderResponse]](
          uri = uri,
          responseAs = asJson[MarginAccountNewOrderResponse],
          requestBody = None,
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey),
          weight = 6
        )
      response <- F.fromEither(responseOrError)
    } yield response
  }
}
