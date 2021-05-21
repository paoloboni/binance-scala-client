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

package io.github.paoloboni.binance.common

import cats.syntax.all._
import io.circe.Decoder

case class KLine(
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

  implicit val klineDecoder: Decoder[KLine] = (
    Decoder[Long].prepare(_.downArray),
    Decoder[BigDecimal].prepare(_.downN(1)),
    Decoder[BigDecimal].prepare(_.downN(2)),
    Decoder[BigDecimal].prepare(_.downN(3)),
    Decoder[BigDecimal].prepare(_.downN(4)),
    Decoder[BigDecimal].prepare(_.downN(5)),
    Decoder[Long].prepare(_.downN(6)),
    Decoder[BigDecimal].prepare(_.downN(7)),
    Decoder[Int].prepare(_.downN(8)),
    Decoder[BigDecimal].prepare(_.downN(9)),
    Decoder[BigDecimal].prepare(_.downN(10))
  ).mapN(KLine.apply)
}
