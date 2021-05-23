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

import io.circe.Decoder
import shapeless.tag
import shapeless.tag.@@
import enumeratum.{CirceEnum, Enum, EnumEntry}

package object common {
  case class Price(symbol: String, price: BigDecimal)
  case class Balance(free: BigDecimal, locked: BigDecimal)

  trait AssetTag
  type Asset = String @@ AssetTag

  implicit val assetDecoder: Decoder[Asset] = Decoder.decodeString.map(tag[AssetTag][String](_))

  trait OrderIdTag
  type OrderId = Long @@ OrderIdTag

  sealed trait OrderSide extends EnumEntry
  object OrderSide extends Enum[OrderSide] with CirceEnum[OrderSide] {
    val values = findValues

    case object SELL extends OrderSide
    case object BUY  extends OrderSide
  }

  case class BinanceBalance(asset: String, free: BigDecimal, locked: BigDecimal)
  case class BinanceBalances(balances: Seq[BinanceBalance])

}
