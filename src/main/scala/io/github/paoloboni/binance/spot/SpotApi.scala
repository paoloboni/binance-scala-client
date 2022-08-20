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
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.ratelimit.RateLimiters
import sttp.client3.UriContext
import sttp.client3.circe.asJson

import scala.util.Try

final class SpotApi[F[_]](
    override val config: SpotConfig,
    client: HttpClient[F],
    rateLimiters: RateLimiters[F]
)(implicit F: Async[F])
    extends BinanceApi[F] {

  override type Config = SpotConfig

  /** Returns the depth of the orderbook.
    *
    * @param query
    *   an `Depth` object containing the query parameters
    * @return
    *   the orderbook depth
    */
  @deprecated("Use [[io.github.paoloboni.binance.spot.SpotApi.V3.getDepth]] instead", "v1.5.2")
  def getDepth(query: common.parameters.DepthParams): F[DepthGetResponse] =
    V3.getDepth(
      spot.parameters.v3
        .DepthParams(query.symbol, query.limit.toString.toIntOption.map(spot.parameters.v3.DepthLimit.apply))
    )

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint in case the result set doesn't
    * fit in a single page.
    *
    * @param query
    *   an `KLines` object containing the query parameters
    * @return
    *   the stream of Kline objects
    */
  @deprecated("Use [[io.github.paoloboni.binance.spot.SpotApi.V3.getKLines]] instead", "v1.5.2")
  def getKLines(query: common.parameters.KLines): Stream[F, KLine] =
    V3.getKLines(
      spot.parameters.v3.KLines(
        symbol = query.symbol,
        interval = query.interval,
        startTime = query.startTime.some,
        endTime = query.endTime.some,
        limit = query.limit
      )
    )

  /** Returns a snapshot of the prices at the time the query is executed.
    *
    * @return
    *   A sequence of prices (one for each symbol)
    */
  @deprecated("Use [[io.github.paoloboni.binance.spot.SpotApi.V3.getPrices]] instead", "v1.5.2")
  def getPrices(): F[Seq[Price]] = V3.getPrices()

  /** Returns the current balance, at the time the query is executed.
    *
    * @return
    *   The account information including the balance (free and locked) for each asset
    */
  @deprecated("Use [[io.github.paoloboni.binance.spot.SpotApi.V3.getBalance]] instead", "v1.5.2")
  def getBalance(): F[SpotAccountInfoResponse] = V3.getBalance()

  /** Creates an order.
    *
    * @param orderCreate
    *   the parameters required to define the order
    *
    * @return
    *   The id of the order created
    */
  @deprecated("Use [[io.github.paoloboni.binance.spot.SpotApi.V3.createOrder]] instead", "v1.5.2")
  def createOrder(orderCreate: SpotOrderCreateParams): F[SpotOrderCreateResponse] = V3.createOrder(orderCreate)

  /** Queries the status and fill price of an existing order.
    *
    * @param orderQuery
    *   The order must be identified by Symbol plus one of OrderId or ClientOrderId
    *
    * @return
    *   Attributes of the order including status, fill amount and filled price.
    */
  @deprecated("Use [[io.github.paoloboni.binance.spot.SpotApi.V3.queryOrder]] instead", "v1.5.2")
  def queryOrder(orderQuery: SpotOrderQueryParams): F[SpotOrderQueryResponse] = V3.queryOrder(orderQuery)

  /** Cancels an order.
    *
    * @param orderCancel
    *   the parameters required to cancel the order
    *
    * @return
    *   currently nothing
    */
  @deprecated("Use [[io.github.paoloboni.binance.spot.SpotApi.V3.cancelOrder]] instead", "v1.5.2")
  def cancelOrder(orderCancel: SpotOrderCancelParams): F[Unit] = V3.cancelOrder(orderCancel)

  /** Cancels all orders of a symbol.
    *
    * @param orderCancel
    *   the parameters required to cancel all the orders
    *
    * @return
    *   currently nothing
    */
  @deprecated("Use [[io.github.paoloboni.binance.spot.SpotApi.V3.cancelAllOrders]] instead", "v1.5.2")
  def cancelAllOrders(orderCancel: SpotOrderCancelAllParams): F[Unit] = V3.cancelAllOrders(orderCancel)

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

  /** The Kline/Candlestick Stream push updates to the current klines/candlestick periodically. In an actively traded
    * symbol, the stream seems to push updates every two seconds. In a less actively traded symbol, there may be longer
    * gaps of 10 secs or more between updates, and updates intervals will not be regularly spaced.
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

  /** API V3
    */
  val V3: SpotRestApiV3[F] = new SpotRestApiV3Impl[F](config, client, rateLimiters)
}

object SpotApi {
  implicit def factory[F[_]](implicit
      F: Async[F]
  ): BinanceApi.Factory[F, SpotApi[F], SpotConfig] =
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
      } yield new SpotApi(config, client, rateLimiters)
}
