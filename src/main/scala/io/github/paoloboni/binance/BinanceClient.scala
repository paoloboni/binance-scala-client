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
import fs2.Stream
import io.circe.generic.auto._
import io.github.paoloboni.binance.RateLimitInterval._
import io.github.paoloboni.encryption.HMAC
import io.github.paoloboni.http.ratelimit.{Rate, RateLimiter}
import io.github.paoloboni.http.{HttpClient, QueryStringConverter}
import io.github.paoloboni.{WithClock, binance}
import io.lemonlabs.uri.{QueryString, Url}
import log.effect.LogWriter
import org.http4s.EntityEncoder
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.blaze.BlazeClientBuilder
import shapeless.tag

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

sealed class BinanceClient[F[_]: WithClock: Async: LogWriter] private (
    config: BinanceConfig,
    client: HttpClient[F],
    rateLimiters: List[RateLimiter[F]]
) extends Decoders {

  private val clock = implicitly[WithClock[F]].clock

  /** Returns a stream of Kline objects. It recursively and lazily invokes the endpoint
    * in case the result set doesn't fit in a single page.
    *
    * @param query an `KLines` object containing the query parameters
    * @return the stream of Kline objects
    */
  def getKLines(query: KLines): Stream[F, KLine] = query match {
    case KLines(symbol, binance.Interval(interval), startTime, endTime, limit) =>
      val url = Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/api/v3/klines",
        query = QueryString.fromPairs(
          "symbol"    -> symbol,
          "interval"  -> interval.toString,
          "startTime" -> startTime.toEpochMilli.toString,
          "endTime"   -> endTime.toEpochMilli.toString,
          "limit"     -> limit.toString
        )
      )

      for {
        rawKlines <- Stream.eval(
          client.get[List[KLine]](url, limiters = rateLimiters.filterNot(_.limitType == RateLimitType.ORDERS))
        )
        klines <- rawKlines match {
          //check if a lone element is enough to fullfill the query. Otherwise a limit of 1 leads
          //to a strange behaviour
          case loneElement :: Nil if (query.endTime.toEpochMilli - loneElement.openTime) > interval.duration.toMillis =>
            val newQuery = query.copy(startTime = Instant.ofEpochMilli(loneElement.closeTime))
            Stream.emit(loneElement) ++ getKLines(newQuery)

          case init :+ last if (query.endTime.toEpochMilli - last.openTime) > interval.duration.toMillis =>
            val newQuery = query.copy(startTime = Instant.ofEpochMilli(last.openTime))
            Stream.emits(init) ++ getKLines(newQuery)

          case list => Stream.emits(list)
        }
      } yield klines

    case other: KLines =>
      Stream.raiseError[F](
        new RuntimeException(s"${other.interval} is not a valid interval for Binance")
      )
  }

  /** Returns a snapshot of the prices at the time the query is executed.
    *
    * @return A sequence of prices (one for each symbol)
    */
  def getPrices(): F[Seq[Price]] = {
    val url = Url(
      scheme = config.scheme,
      host = config.host,
      port = config.port,
      path = "/api/v3/ticker/price"
    )
    for {
      prices <- client.get[List[Price]](
        url = url,
        limiters = rateLimiters.filterNot(_.limitType == RateLimitType.ORDERS),
        weight = 2
      )
    } yield prices
  }

  /** Returns the current balance, at the time the query is executed.
    *
    * @return The balance (free and locked) for each asset
    */
  def getBalance(): F[Map[Asset, Balance]] = {
    def url(currentMillis: Long) = {
      val query       = s"recvWindow=5000&timestamp=${currentMillis.toString}"
      val signature   = HMAC.sha256(config.apiSecret, query)
      val queryString = QueryString.parse(query).addParam("signature", signature)
      Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/api/v3/account",
        query = queryString
      )
    }
    for {
      currentTime <- clock.realTime
      balances <- client.get[BinanceBalances](
        url = url(currentTime.toMillis),
        limiters = rateLimiters.filterNot(_.limitType == RateLimitType.ORDERS),
        headers = Map("X-MBX-APIKEY" -> config.apiKey),
        weight = 5
      )
    } yield balances.balances.map(b => tag[AssetTag](b.asset) -> Balance(b.free, b.locked)).toMap
  }

  private implicit val orderSideQueryStringConverter: QueryStringConverter[OrderSide] =
    QueryStringConverter.enumEntryConverter(OrderSide)
  private implicit val orderTypeQueryStringConverter: QueryStringConverter[OrderType] =
    QueryStringConverter.enumEntryConverter(OrderType)
  private implicit val timeInForceQueryStringConverter: QueryStringConverter[TimeInForce] =
    QueryStringConverter.enumEntryConverter(TimeInForce)
  private implicit val orderCreateResponseTypeQueryStringConverter: QueryStringConverter[OrderCreateResponseType] =
    QueryStringConverter.enumEntryConverter(OrderCreateResponseType)

  /** Creates an order.
    *
    * @param orderCreate the parameters required to define the order
    *
    * @return The id of the order created
    */
  def createOrder(orderCreate: OrderCreate): F[OrderId] = {

    implicit val encodeString: EntityEncoder[F, String] = EntityEncoder.showEncoder

    def urlAndBody(currentMillis: Long) = {
      val requestBody = QueryString
        .parse(QueryStringConverter[OrderCreate].to(orderCreate))
        .addParams(
          "recvWindow" -> "5000",
          "timestamp"  -> currentMillis.toString
        )
      val signature = HMAC.sha256(config.apiSecret, requestBody.toString())
      val url = Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/api/v3/order"
      )
      (url, requestBody.addParam("signature" -> signature))
    }
    for {
      currentTime <- clock.realTime
      (url, requestBody) = urlAndBody(currentTime.toMillis)
      orderId <- client
        .post[String, CreateOrderResponse](
          url = url,
          requestBody = requestBody.toString(),
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey)
        )
        .map(response => tag[OrderIdTag][String](response.orderId.toString))
    } yield orderId
  }

  /** Cancles an order.
    *
    * @param orderCancel the parameters required to cancle the order
    *
    * @return currently nothing
    */
  def candleOrder(orderCancel: OrderCancle): F[Unit] = {

    def urlAndBody(currentMillis: Long) = {
      val requestBody = QueryStringConverter[OrderCancle].to(orderCancel) + s"&recvWindow=5000&timestamp=$currentMillis"
      val signature   = HMAC.sha256(config.apiSecret, requestBody)
      val url = Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/api/v3/order"
      )
      (url, requestBody + s"&signature=$signature")
    }

    for {
      currentTime <- clock.realTime
      (url, requestBody) = urlAndBody(currentTime.toMillis)
      _ <- client
        .delete[String, Unit](
          url = url,
          requestBody = requestBody,
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey),
          weight = 1
        )
    } yield ()
  }

  /** Cancles all open orders for a symbol.
    *
    * @param orderCancelAll the parameters required to cancle the orders
    *
    * @return currently nothing
    */
  def candleAllOrders(orderCancleAll: OrderCancleAll): F[Unit] = {

    def urlAndBody(currentMillis: Long) = {
      val requestBody =
        QueryStringConverter[OrderCancleAll].to(orderCancleAll) + s"&recvWindow=5000&timestamp=$currentMillis"
      val signature = HMAC.sha256(config.apiSecret, requestBody)
      val url = Url(
        scheme = config.scheme,
        host = config.host,
        port = config.port,
        path = "/api/v3/openOrders"
      )
      (url, requestBody + s"&signature=$signature")
    }

    for {
      currentTime <- clock.realTime
      (url, requestBody) = urlAndBody(currentTime.toMillis)
      _ <- client
        .delete[String, Unit](
          url = url,
          requestBody = requestBody,
          limiters = rateLimiters,
          headers = Map("X-MBX-APIKEY" -> config.apiKey),
          weight = 1
        )
    } yield ()
  }
}

object BinanceClient {

  def apply[F[_]: WithClock: LogWriter: Async](
      config: BinanceConfig
  ): Resource[F, BinanceClient[F]] = {

    BlazeClientBuilder[F](global)
      .withResponseHeaderTimeout(config.responseHeaderTimeout)
      .withMaxTotalConnections(config.maxTotalConnections)
      .resource
      .evalMap { implicit c =>
        def requestRateLimits(client: HttpClient[F]) = for {
          rateLimits <- client.get[RateLimits](
            Url(
              scheme = config.scheme,
              host = config.host,
              port = config.port,
              path = config.infoUrl
            ),
            limiters = List.empty
          )
          requestLimits = rateLimits.rateLimits
            .map(limit =>
              Rate(
                limit.limit,
                limit.interval match {
                  case SECOND => limit.intervalNum.seconds
                  case MINUTE => limit.intervalNum.minutes
                  case DAY    => limit.intervalNum.days
                },
                limit.rateLimitType
              )
            )
        } yield requestLimits

        for {
          client <- HttpClient.make[F]
          limits <- requestRateLimits(client)
          limiters <- limits
            .map(limit => RateLimiter.make[F](limit.perSecond, config.rateLimiterBufferSize, limit.limitType))
            .sequence
        } yield new BinanceClient(config, client, limiters)
      }
  }
}
