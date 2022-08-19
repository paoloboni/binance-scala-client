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

package io.github.paoloboni.binance.spot

import cats.effect.Async
import io.github.paoloboni.binance.{BinanceApi, spot}
import io.github.paoloboni.binance.common.parameters.DepthParams
import io.github.paoloboni.binance.common.response._
import io.github.paoloboni.binance.common.{KLine, Price, SpotConfig}
import io.github.paoloboni.binance.spot.parameters.{
  SpotOrderCancelAllParams,
  SpotOrderCancelParams,
  SpotOrderCreateParams,
  SpotOrderQueryParams
}
import io.github.paoloboni.binance.spot.response.{
  SpotAccountInfoResponse,
  SpotOrderCreateResponse,
  SpotOrderQueryResponse
}
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.ratelimit.RateLimiters

import java.time.Instant

trait SpotRestApiV3[F[_]] extends BinanceApi[F] {

  override type Config = SpotConfig

  /** Returns the depth of the orderbook.
    *
    * @param query
    *   an `Depth` object containing the query parameters
    * @return
    *   the orderbook depth
    */
  def getDepth(query: DepthParams): F[DepthGetResponse]

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint in case the result set doesn't
    * fit in a single page.
    *
    * @param query
    *   an `KLines` object containing the query parameters
    * @return
    *   the stream of Kline objects
    */
  def getKLines(query: spot.parameters.v3.KLines): fs2.Stream[F, KLine]

  /** Returns a snapshot of the prices at the time the query is executed.
    *
    * @return
    *   A sequence of prices (one for each symbol)
    */
  def getPrices(): F[Seq[Price]]

  /** Returns the current balance, at the time the query is executed.
    *
    * @return
    *   The account information including the balance (free and locked) for each asset
    */
  def getBalance(): F[SpotAccountInfoResponse]

  /** Creates an order.
    *
    * @param orderCreate
    *   the parameters required to define the order
    * @return
    *   The id of the order created
    */
  def createOrder(orderCreate: SpotOrderCreateParams): F[SpotOrderCreateResponse]

  /** Queries the status and fill price of an existing order.
    *
    * @param orderQuery
    *   The order must be identified by Symbol plus one of OrderId or ClientOrderId
    * @return
    *   Attributes of the order including status, fill amount and filled price.
    */
  def queryOrder(orderQuery: SpotOrderQueryParams): F[SpotOrderQueryResponse]

  /** Cancels an order.
    *
    * @param orderCancel
    *   the parameters required to cancel the order
    * @return
    *   currently nothing
    */
  def cancelOrder(orderCancel: SpotOrderCancelParams): F[Unit]

  /** Cancels all orders of a symbol.
    *
    * @param orderCancel
    *   the parameters required to cancel all the orders
    * @return
    *   currently nothing
    */
  def cancelAllOrders(orderCancel: SpotOrderCancelAllParams): F[Unit]
}

class SpotRestApiV3Impl[F[_]](
    override val config: SpotConfig,
    client: HttpClient[F],
    rateLimiters: RateLimiters[F]
)(implicit F: Async[F])
    extends SpotRestApiV3[F] {

  import cats.syntax.all._
  import fs2.Stream
  import io.circe.generic.auto._
  import io.github.paoloboni.binance.common._
  import io.github.paoloboni.binance.common.response._
  import io.github.paoloboni.binance.spot.parameters._
  import io.github.paoloboni.binance.spot.response._
  import io.github.paoloboni.binance.{common, spot}
  import io.github.paoloboni.encryption.MkSignedUri
  import io.github.paoloboni.http.QueryParamsConverter._
  import sttp.client3.circe.{asJson, _}

  private val mkSignedUri = new MkSignedUri[F](
    recvWindow = config.recvWindow,
    apiSecret = config.apiSecret
  )

  private val baseUrl        = config.restBaseUrl.addPath("api", "v3")
  private val depthUri       = baseUrl.addPath("depth")
  private val kLinesUri      = baseUrl.addPath("klines")
  private val tickerPriceUri = baseUrl.addPath("ticker", "price")
  private val accountUri     = baseUrl.addPath("account")
  private val orderUri       = baseUrl.addPath("order")
  private val openOrdersUri  = baseUrl.addPath("openOrders")

  override def getDepth(query: common.parameters.DepthParams): F[DepthGetResponse] = {
    val uri = depthUri.addParams(query.toQueryParams)
    for {
      depthOrError <- client.get[CirceResponse[DepthGetResponse]](
        uri = uri,
        responseAs = asJson[DepthGetResponse],
        limiters = rateLimiters.requestsOnly,
        weight = query.limit.weight
      )
      depth <- F.fromEither(depthOrError)
    } yield depth
  }

  override def getKLines(query: spot.parameters.v3.KLines): Stream[F, KLine] = {
    val uri = kLinesUri.addParams(query.toQueryParams)
    for {
      response <- Stream.eval(
        client.get[CirceResponse[List[KLine]]](
          uri = uri,
          responseAs = asJson[List[KLine]],
          limiters = rateLimiters.requestsOnly
        )
      )
      rawKlines <- Stream.eval(F.fromEither(response))
      klines <- rawKlines match {
        // check if a lone element is enough to fulfill the query. Otherwise a limit of 1 leads
        // to a strange behaviour
        case loneElement :: Nil
            if query.endTime
              .exists(end => (end.toEpochMilli - loneElement.openTime) > query.interval.duration.toMillis) =>
          val newQuery = query.copy(startTime = Instant.ofEpochMilli(loneElement.closeTime).some)
          Stream.emit(loneElement) ++ getKLines(newQuery)

        case init :+ last
            if query.endTime
              .exists(end => (end.toEpochMilli - last.openTime) > query.interval.duration.toMillis) =>
          val newQuery = query.copy(startTime = Instant.ofEpochMilli(last.openTime).some)
          Stream.emits(init) ++ getKLines(newQuery)

        case list => Stream.emits(list)
      }
    } yield klines
  }

  override def getPrices(): F[Seq[Price]] = for {
    pricesOrError <- client.get[CirceResponse[List[Price]]](
      uri = tickerPriceUri,
      responseAs = asJson[List[Price]],
      limiters = rateLimiters.requestsOnly,
      weight = 2
    )
    prices <- F.fromEither(pricesOrError)
  } yield prices

  override def getBalance(): F[SpotAccountInfoResponse] =
    for {
      uri <- mkSignedUri(accountUri)
      responseOrError <- client.get[CirceResponse[SpotAccountInfoResponse]](
        uri = uri,
        responseAs = asJson[SpotAccountInfoResponse],
        limiters = rateLimiters.requestsOnly,
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 10
      )
      response <- F.fromEither(responseOrError)
    } yield response

  override def createOrder(orderCreate: SpotOrderCreateParams): F[SpotOrderCreateResponse] = {

    val params = orderCreate.toQueryParams.param("newOrderRespType", SpotOrderCreateResponseType.FULL.toString).toSeq
    for {
      uri <- mkSignedUri(
        uri = orderUri,
        params: _*
      )
      responseOrError <- client
        .post[String, CirceResponse[SpotOrderCreateResponse]](
          uri = uri,
          responseAs = asJson[SpotOrderCreateResponse],
          requestBody = None,
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      response <- F.fromEither(responseOrError)
    } yield response
  }

  override def queryOrder(orderQuery: SpotOrderQueryParams): F[SpotOrderQueryResponse] =
    for {
      uri <- mkSignedUri(
        uri = orderUri,
        orderQuery.toQueryParams.toSeq: _*
      )
      responseOrError <- client.get[CirceResponse[SpotOrderQueryResponse]](
        uri = uri,
        responseAs = asJson[SpotOrderQueryResponse],
        limiters = rateLimiters.requestsOnly,
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 2
      )
      response <- F.fromEither(responseOrError)
    } yield response

  override def cancelOrder(orderCancel: SpotOrderCancelParams): F[Unit] =
    for {
      uri <- mkSignedUri(
        uri = orderUri,
        orderCancel.toQueryParams.toSeq: _*
      )
      res <- client
        .delete[CirceResponse[io.circe.Json]](
          uri = uri,
          responseAs = asJson[io.circe.Json],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      _ <- F.fromEither(res)
    } yield ()

  override def cancelAllOrders(orderCancel: SpotOrderCancelAllParams): F[Unit] =
    for {
      uri <- mkSignedUri(openOrdersUri, orderCancel.toQueryParams.toSeq: _*)
      res <- client
        .delete[CirceResponse[io.circe.Json]](
          uri = uri,
          responseAs = asJson[io.circe.Json],
          limiters = rateLimiters.value,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      _ <- F.fromEither(res)
    } yield ()
}
