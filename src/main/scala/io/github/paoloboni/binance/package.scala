/*
 * Copyright (c) 2020 Paolo Boni
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

package io.github.paoloboni

import java.time.Instant

import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.circe.Decoder
import shapeless.tag.@@

import scala.collection.immutable
import scala.concurrent.duration.{Duration, _}

package object binance {
  case class BinanceConfig(
      scheme: String,
      host: String,
      port: Int,
      infoUrl: String,
      apiKey: String,
      apiSecret: String,
      responseHeaderTimeout: Duration = 60.seconds,
      maxTotalConnections: Int = 20
  )

  final case class KLines(symbol: String, interval: Duration, startTime: Instant, endTime: Instant, limit: Int)

  case class Price(symbol: String, price: BigDecimal)

  trait AssetTag
  type Asset = String @@ AssetTag

  trait OrderIdTag
  type OrderId = String @@ OrderIdTag

  case class Balance(free: BigDecimal, locked: BigDecimal)

  final case class KLine(
      openTime: Long,
      open: BigDecimal,
      high: BigDecimal,
      low: BigDecimal,
      close: BigDecimal,
      volume: BigDecimal,
      closeTime: Long,
      quoteAssetVolume: BigDecimal,
      numberOfTrades: Int,
      takerBuyBaseAssetVolume: BigDecimal,
      takerBuyQuoteAssetVolume: BigDecimal
  )

  object KLine {
    implicit class Ops(val kline: KLine) extends AnyVal {
      def convert(other: KLine): KLine = KLine(
        kline.openTime,
        kline.open * other.open,
        kline.high * other.high,
        kline.low * other.low,
        kline.close * other.close,
        kline.volume,
        kline.closeTime,
        kline.quoteAssetVolume,
        kline.numberOfTrades,
        kline.takerBuyBaseAssetVolume,
        kline.takerBuyQuoteAssetVolume
      )
    }
  }

  sealed trait OrderSide extends EnumEntry

  object OrderSide extends Enum[OrderSide] {
    val values = findValues

    case object SELL extends OrderSide
    case object BUY  extends OrderSide
  }

  sealed trait OrderType extends EnumEntry

  object OrderType extends Enum[OrderType] {
    val values = findValues

    case object LIMIT             extends OrderType
    case object MARKET            extends OrderType
    case object STOP_LOSS         extends OrderType
    case object STOP_LOSS_LIMIT   extends OrderType
    case object TAKE_PROFIT       extends OrderType
    case object TAKE_PROFIT_LIMIT extends OrderType
    case object LIMIT_MAKER       extends OrderType
  }

  sealed trait TimeInForce extends EnumEntry

  object TimeInForce extends Enum[TimeInForce] {
    val values = findValues

    case object GTC extends TimeInForce // Good-Til-Canceled
    case object IOT extends TimeInForce // Immediate or Cancel
    case object FOK extends TimeInForce // Fill or Kill
  }

  sealed trait OrderCreateResponseType extends EnumEntry

  object OrderCreateResponseType extends Enum[OrderCreateResponseType] {
    val values = findValues

    case object ACK    extends OrderCreateResponseType
    case object RESULT extends OrderCreateResponseType
    case object FULL   extends OrderCreateResponseType
  }

  case class OrderCreate(
      symbol: String,
      side: OrderSide,
      `type`: OrderType,
      timeInForce: Option[TimeInForce],
      quantity: BigDecimal,
      price: Option[BigDecimal],
      newClientOrderId: Option[String],
      stopPrice: Option[BigDecimal],
      icebergQty: Option[BigDecimal],
      newOrderRespType: Option[OrderCreateResponseType]
  )

  sealed trait RateLimitType extends EnumEntry
  object RateLimitType extends Enum[RateLimitType] with CirceEnum[RateLimitType] {

    override def values = findValues

    case object REQUEST_WEIGHT extends RateLimitType
    case object ORDERS         extends RateLimitType
    case object RAW_REQUESTS   extends RateLimitType
  }

  sealed trait RateLimitInterval extends EnumEntry
  object RateLimitInterval extends Enum[RateLimitInterval] with CirceEnum[RateLimitInterval] {

    override def values = findValues

    case object SECOND extends RateLimitInterval
    case object MINUTE extends RateLimitInterval
    case object DAY    extends RateLimitInterval
  }

  case class RateLimit(rateLimitType: RateLimitType, interval: RateLimitInterval, intervalNum: Int, limit: Int)

  trait Decoders {
    import cats.syntax.all._

    implicit val klineDecoder: Decoder[KLine] = (
      Decoder[Long].prepare(_.downArray),
      Decoder[BigDecimal].prepare(_.downArray.rightN(1)),
      Decoder[BigDecimal].prepare(_.downArray.rightN(2)),
      Decoder[BigDecimal].prepare(_.downArray.rightN(3)),
      Decoder[BigDecimal].prepare(_.downArray.rightN(4)),
      Decoder[BigDecimal].prepare(_.downArray.rightN(5)),
      Decoder[Long].prepare(_.downArray.rightN(6)),
      Decoder[BigDecimal].prepare(_.downArray.rightN(7)),
      Decoder[Int].prepare(_.downArray.rightN(8)),
      Decoder[BigDecimal].prepare(_.downArray.rightN(9)),
      Decoder[BigDecimal].prepare(_.downArray.rightN(10))
    ).mapN(KLine.apply)
  }

  object Decoders extends Decoders

  sealed abstract class Interval(val duration: Duration) extends EnumEntry

  object Interval extends Enum[Interval] {
    val values: immutable.IndexedSeq[Interval] = findValues

    case object `1m`  extends Interval(1.minute)
    case object `3m`  extends Interval(3.minutes)
    case object `5m`  extends Interval(5.minutes)
    case object `15m` extends Interval(15.minutes)
    case object `30m` extends Interval(30.minutes)
    case object `1h`  extends Interval(1.hour)
    case object `2h`  extends Interval(2.hours)
    case object `4h`  extends Interval(4.hours)
    case object `6h`  extends Interval(6.hours)
    case object `8h`  extends Interval(8.hours)
    case object `12h` extends Interval(12.hours)
    case object `1d`  extends Interval(1.day)
    case object `3d`  extends Interval(3.days)
    case object `1w`  extends Interval(7.days)

    private val durationMap = values.map(entry => entry.duration -> entry).toMap

    implicit class Ops(val interval: Interval) extends AnyVal {
      def asDuration: Duration = interval.duration
    }
    implicit class DurationOps(val duration: Duration) extends AnyVal {
      def asBinanceInterval: Option[Interval] = durationMap.get(duration)
    }

    def unapply(arg: Duration): Option[Interval] = arg.asBinanceInterval
  }

  case class BinanceBalance(asset: String, free: BigDecimal, locked: BigDecimal)
  case class BinanceBalances(balances: Seq[BinanceBalance])

  case class CreateOrderResponse(orderId: Long)
}
