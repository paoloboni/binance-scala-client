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
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.Stream
import io.circe.generic.auto._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.response._
import io.github.paoloboni.binance.fapi.response.AggregateTradeStream
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.spot.response._
import io.github.paoloboni.binance.{BinanceApi, common, spot}
import io.github.paoloboni.encryption.MkSignedUri
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.QueryParamsConverter._
import io.github.paoloboni.http.ratelimit.RateLimiters
import sttp.client3.UriContext
import sttp.client3.circe.{asJson, _}

import java.time.Instant
import scala.util.Try

final case class SpotApi[F[_]](
    config: SpotConfig[F],
    client: HttpClient[F],
    exchangeInfo: spot.response.ExchangeInformation,
    rateLimiters: RateLimiters[F]
)(implicit F: Async[F])
    extends BinanceApi[F] {

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

  /** Returns the depth of the orderbook.
    *
    * @param query
    *   an `Depth` object containing the query parameters
    * @return
    *   the orderbook depth
    */
  def getDepth(query: common.parameters.DepthParams): F[DepthGetResponse] = {
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

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint in case the result set doesn't
    * fit in a single page.
    *
    * @param query
    *   an `KLines` object containing the query parameters
    * @return
    *   the stream of Kline objects
    */
  def getKLines(query: common.parameters.KLines): Stream[F, KLine] = {
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
            if (query.endTime.toEpochMilli - loneElement.openTime) > query.interval.duration.toMillis =>
          val newQuery = query.copy(startTime = Instant.ofEpochMilli(loneElement.closeTime))
          Stream.emit(loneElement) ++ getKLines(newQuery)

        case init :+ last if (query.endTime.toEpochMilli - last.openTime) > query.interval.duration.toMillis =>
          val newQuery = query.copy(startTime = Instant.ofEpochMilli(last.openTime))
          Stream.emits(init) ++ getKLines(newQuery)

        case list => Stream.emits(list)
      }
    } yield klines
  }

  /** Returns a snapshot of the prices at the time the query is executed.
    *
    * @return
    *   A sequence of prices (one for each symbol)
    */
  def getPrices(): F[Seq[Price]] = for {
    pricesOrError <- client.get[CirceResponse[List[Price]]](
      uri = tickerPriceUri,
      responseAs = asJson[List[Price]],
      limiters = rateLimiters.requestsOnly,
      weight = 2
    )
    prices <- F.fromEither(pricesOrError)
  } yield prices

  /** Returns the current balance, at the time the query is executed.
    *
    * @return
    *   The account information including the balance (free and locked) for each asset
    */
  def getBalance(): F[SpotAccountInfoResponse] =
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

  /** Creates an order.
    *
    * @param orderCreate
    *   the parameters required to define the order
    *
    * @return
    *   The id of the order created
    */
  def createOrder(orderCreate: SpotOrderCreateParams): F[SpotOrderCreateResponse] = {

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

  /** Queries the status and fill price of an existing order.
    *
    * @param orderQuery
    *   The order must be identified by Symbol plus one of OrderId or ClientOrderId
    *
    * @return
    *   Attributes of the order including status, fill amount and filled price.
    */
  def queryOrder(orderQuery: SpotOrderQueryParams): F[SpotOrderQueryResponse] =
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

  /** Cancels an order.
    *
    * @param orderCancel
    *   the parameters required to cancel the order
    *
    * @return
    *   currently nothing
    */
  def cancelOrder(orderCancel: SpotOrderCancelParams): F[Unit] =
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

  /** Cancels all orders of a symbol.
    *
    * @param orderCancel
    *   the parameters required to cancel all the orders
    *
    * @return
    *   currently nothing
    */
  def cancelAllOrders(orderCancel: SpotOrderCancelAllParams): F[Unit] =
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

  /** The Trade Streams push raw trade information; each trade has a unique buyer and seller.
    *
    * @param symbol
    *   the symbol
    * @return
    *   a stream of trades
    */
  def tradeStreams(symbol: String): Stream[F, TradeStream] =
    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@trade").toEither)
      )
      stream <- client.ws[TradeStream](uri)
    } yield stream

  /** The Kline/Candlestick Stream push updates to the current klines/candlestick periodically. 
  In an actively traded symbol, the stream seems to push updates every two seconds. In a less actively traded symbol,
  there may be longer gaps of 10 secs or more between updates, and updates intervals will not be regularly spaced.
    *
    * @param symbol
    *   the symbol
    * @param interval
    *   the interval
    * @return
    *   a stream of klines
    */
  def kLineStreams(symbol: String, interval: Interval): Stream[F, KLineStream] =
    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@kline_${interval.toString}").toEither)
      )
      stream <- client.ws[KLineStream](uri)
    } yield stream

  /** Order book price and quantity depth updates used to locally manage an order book.
    *
    * @param symbol
    *   the symbol
    * @return
    *   a stream of order book price and quantity depth updates
    */
  def diffDepthStream(symbol: String): Stream[F, DiffDepthStream] =
    for {
      uri    <- Stream.eval(F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@depth").toEither))
      stream <- client.ws[DiffDepthStream](uri)
    } yield stream

  /** Top bids and asks
    *
    * @param symbol
    *   the symbol
    * @param level
    *   the level
    * @return
    *   a stream of top bids and asks
    */
  def partialBookDepthStream(symbol: String, level: Level): Stream[F, PartialDepthStream] =
    for {
      uri <- Stream.eval(
        F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@depth${level.toString}").toEither)
      )
      stream <- client.ws[PartialDepthStream](uri)
    } yield stream

  /** Pushes any update to the best bid or ask's price or quantity in real-time for all symbols.
    *
    * @return
    *   a stream of best bid or ask's price or quantity for all symbols
    */
  def allBookTickersStream(): Stream[F, BookTicker] =
    for {
      uri    <- Stream.eval(F.fromEither(Try(uri"${config.wsBaseUrl}/ws/!bookTicker").toEither))
      stream <- client.ws[BookTicker](uri)
    } yield stream

  /** The Aggregate Trade Streams push trade information that is aggregated for a single taker order every 100
    * milliseconds.
    *
    * @param symbol
    *   the symbol
    * @return
    *   a stream of aggregate trade events
    */
  def aggregateTradeStreams(symbol: String): Stream[F, AggregateTradeStream] =
    for {
      uri    <- Stream.eval(F.fromEither(Try(uri"${config.wsBaseUrl}/ws/${symbol.toLowerCase}@aggTrade").toEither))
      stream <- client.ws[AggregateTradeStream](uri)
    } yield stream
}

object SpotApi {
  implicit def factory[F[_]](implicit
      F: Async[F]
  ): BinanceApi.Factory[F, SpotApi[F], SpotConfig[F]] =
    (config: SpotConfig[F], client: HttpClient[F]) =>
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
      } yield SpotApi.apply(config, client, exchangeInfo, rateLimiters)
}
