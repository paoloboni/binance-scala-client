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

package io.github.paoloboni.binance.fapi

import cats.effect.Async
import cats.effect.std.Queue
import cats.implicits._
import fs2.{Pipe, Stream}
import io.circe.generic.auto._
import io.circe.parser._
import io.github.paoloboni.WithClock
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.parameters.TimeParams
import io.github.paoloboni.binance.common.response.CirceResponse
import io.github.paoloboni.binance.fapi.parameters._
import io.github.paoloboni.binance.fapi.response._
import io.github.paoloboni.binance.{BinanceApi, common, fapi}
import io.github.paoloboni.encryption.HMAC
import io.github.paoloboni.http.HttpClient
import io.github.paoloboni.http.QueryStringConverter.Ops
import io.github.paoloboni.http.ratelimit.RateLimiter
import io.lemonlabs.uri.QueryString
import io.lemonlabs.uri.typesafe.dsl._
import log.effect.LogWriter
import sttp.client3.ResponseAsByteArray
import sttp.client3.circe.{asJson, _}
import sttp.ws.WebSocketFrame

import java.time.Instant

final case class FutureApi[F[_]: WithClock: LogWriter](
    config: FapiConfig,
    client: HttpClient[F],
    exchangeInfo: fapi.response.ExchangeInformation,
    rateLimiters: List[RateLimiter[F]]
)(implicit F: Async[F])
    extends BinanceApi[F] {

  private val clock = implicitly[WithClock[F]].clock

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint
    * in case the result set doesn't fit in a single page.
    *
    * @param query an `KLines` object containing the query parameters
    * @return the stream of Kline objects
    */
  def getKLines(query: common.parameters.KLines): Stream[F, KLine] = {
    val url = (config.restBaseUrl / "fapi/v1/klines").withQueryString(query.toQueryString)

    for {
      response <- Stream.eval(
        client.get[CirceResponse[List[KLine]]](
          url = url,
          responseAs = asJson[List[KLine]],
          limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS)
        )
      )
      rawKlines <- Stream.eval(F.fromEither(response))
      klines <- rawKlines match {
        //check if a lone element is enough to fulfill the query. Otherwise a limit of 1 leads
        //to a strange behaviour
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
    * @return A sequence of prices (one for each symbol)
    */
  def getPrices(): F[Seq[Price]] = {
    val url = (config.restBaseUrl / "fapi/v1/ticker/price")
    for {
      pricesOrError <- client.get[CirceResponse[List[Price]]](
        url = url,
        responseAs = asJson[List[Price]],
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS),
        weight = 2
      )
      prices <- F.fromEither(pricesOrError)
    } yield prices
  }

  /** Returns the latest price for a symbol.
    *
    * @param symbol The symbol
    * @return The price for the symbol
    */
  def getPrice(symbol: String): F[Price] = {
    val url = (config.restBaseUrl / "fapi/v1/ticker/price").withQueryString(QueryString.fromPairs("symbol" -> symbol))

    client
      .get[CirceResponse[Price]](
        url = url,
        responseAs = asJson[Price],
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS)
      )
      .flatMap(F.fromEither)
  }

  /** Change user's position mode (Hedge Mode or One-way Mode ) on EVERY symbol
    *
    * @param dualSidePosition "true": Hedge Mode; "false": One-way Mode
    * @return Unit
    */
  def changePositionMode(dualSidePosition: Boolean): F[Unit] = {
    def url(currentMillis: Long) = {
      val timeParams  = TimeParams(config.recvWindow, currentMillis).toQueryString
      val queryString = QueryString.fromPairs("dualSidePosition" -> dualSidePosition).addParams(timeParams)
      val signature   = HMAC.sha256(config.apiSecret, queryString.toString())
      (config.restBaseUrl / "fapi/v1/positionSide/dual").withQueryString(
        queryString.addParam("signature" -> signature)
      )
    }

    for {
      currentTime <- clock.realTime
      _ <- client
        .post[String, Array[Byte]](
          url = url(currentTime.toMillis),
          responseAs = ResponseAsByteArray,
          requestBody = None,
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
    } yield ()

  }

  /** Change user's initial leverage of specific symbol market.
    *
    * @param changeLeverage request parameters
    * @return the new leverage
    */
  def changeInitialLeverage(changeLeverage: ChangeInitialLeverageParams): F[ChangeInitialLeverageResponse] = {

    def url(currentMillis: Long) = {
      val timeParams  = TimeParams(config.recvWindow, currentMillis).toQueryString
      val queryString = changeLeverage.toQueryString.addParams(timeParams)
      val signature   = HMAC.sha256(config.apiSecret, queryString.toString())
      (config.restBaseUrl / "fapi/v1/leverage").withQueryString(queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- clock.realTime
      responseOrError <- client
        .post[String, CirceResponse[ChangeInitialLeverageResponse]](
          url = url(currentTime.toMillis),
          responseAs = asJson[ChangeInitialLeverageResponse],
          requestBody = None,
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      response <- F.fromEither(responseOrError)
    } yield response
  }

  /** Returns the current balance, at the time the query is executed.
    *
    * @return The balance (free and locked) for each asset
    */
  def getBalance(): F[FutureAccountInfoResponse] = {
    def url(currentMillis: Long) = {
      val query       = TimeParams(config.recvWindow, currentMillis).toQueryString
      val signature   = HMAC.sha256(config.apiSecret, query.toString())
      val queryString = query.addParam("signature", signature)
      (config.restBaseUrl / "fapi/v1/account").withQueryString(queryString)
    }
    for {
      currentTime <- clock.realTime
      balanceOrError <- client.get[CirceResponse[FutureAccountInfoResponse]](
        url = url(currentTime.toMillis),
        responseAs = asJson[FutureAccountInfoResponse],
        limiters = rateLimiters.filterNot(_.limitType == common.response.RateLimitType.ORDERS),
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 5
      )
      balance <- F.fromEither(balanceOrError)
    } yield balance
  }

  /** Creates an order.
    *
    * @param orderCreate the parameters required to define the order
    *
    * @return The id of the order created
    */
  def createOrder(orderCreate: FutureOrderCreateParams): F[FutureOrderCreateResponse] = {

    def url(currentMillis: Long) = {
      val timeParams = TimeParams(config.recvWindow, currentMillis).toQueryString
      val queryString = orderCreate.toQueryString
        .addParams(timeParams)
        .addParam("newOrderRespType" -> FutureOrderCreateResponseType.RESULT.entryName)
      val signature = HMAC.sha256(config.apiSecret, queryString.toString())
      (config.restBaseUrl / "fapi/v1/order").withQueryString(queryString.addParam("signature" -> signature))
    }

    for {
      currentTime <- clock.realTime
      responseOrError <- client
        .post[String, CirceResponse[FutureOrderCreateResponse]](
          url = url(currentTime.toMillis),
          responseAs = asJson[FutureOrderCreateResponse],
          requestBody = None,
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
      response <- F.fromEither(responseOrError)
    } yield response
  }

  /** The Aggregate Trade Streams push trade information that is aggregated for a single taker order every 100 milliseconds.
    *
    * @param symbol the symbol
    * @return a stream of aggregate trade events
    */
  def aggregateTradeStreams(symbol: String): Stream[F, AggregateTrade] =
    client.ws[AggregateTrade](config.wsBaseUrl / s"ws/${symbol.toLowerCase}@aggTrade")
}

object FutureApi {
  implicit def factory[F[_]: WithClock: LogWriter](implicit
      F: Async[F]
  ): BinanceApi.Factory[F, FutureApi[F], FapiConfig] =
    (config: FapiConfig, client: HttpClient[F]) =>
      for {
        exchangeInfoEither <- client
          .get[CirceResponse[fapi.response.ExchangeInformation]](
            url = config.exchangeInfoUrl,
            responseAs = asJson[fapi.response.ExchangeInformation],
            limiters = List.empty
          )
        exchangeInfo <- F.fromEither(exchangeInfoEither)
        rateLimiters <- exchangeInfo.createRateLimiters(config.rateLimiterBufferSize)
      } yield FutureApi.apply(config, client, exchangeInfo, rateLimiters)
}
