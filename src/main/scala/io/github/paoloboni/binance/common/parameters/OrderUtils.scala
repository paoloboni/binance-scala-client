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

package io.github.paoloboni.binance.common.parameters

import enumeratum.{Enum, EnumEntry}

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
