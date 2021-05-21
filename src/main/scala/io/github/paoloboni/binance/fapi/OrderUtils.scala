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

import enumeratum.{CirceEnum, Enum, EnumEntry}

sealed trait OrderType extends EnumEntry
object OrderType extends Enum[OrderType] with CirceEnum[OrderType] {
  val values = findValues

  case object LIMIT                extends OrderType
  case object MARKET               extends OrderType
  case object STOP                 extends OrderType
  case object STOP_MARKET          extends OrderType
  case object TAKE_PROFIT          extends OrderType
  case object TAKE_PROFIT_MARKET   extends OrderType
  case object TRAILING_STOP_MARKET extends OrderType
}

sealed trait TimeInForce extends EnumEntry
object TimeInForce extends Enum[TimeInForce] with CirceEnum[TimeInForce] {
  val values = findValues

  case object GTC extends TimeInForce // Good-Til-Canceled
  case object IOC extends TimeInForce // Immediate or Cancel
  case object FOK extends TimeInForce // Fill or Kill
  case object GTX extends TimeInForce // Good Till Crossing (Post Only)
}

sealed trait PositionSide extends EnumEntry
object PositionSide extends Enum[PositionSide] with CirceEnum[PositionSide] {
  val values = findValues

  case object SHORT extends PositionSide // Good-Til-Canceled
  case object LONG  extends PositionSide // Immediate or Cancel
  case object BOTH  extends PositionSide // Fill or Kill
}

sealed trait OrderStatus extends EnumEntry
object OrderStatus extends Enum[OrderStatus] with CirceEnum[OrderStatus] {
  val values = findValues

  case object NEW              extends OrderStatus
  case object PARTIALLY_FILLED extends OrderStatus
  case object FILLED           extends OrderStatus
  case object CANCELED         extends OrderStatus
  case object REJECTED         extends OrderStatus
  case object EXPIRED          extends OrderStatus

}

sealed trait ContractStatus extends EnumEntry
object ContractStatus extends Enum[ContractStatus] with CirceEnum[ContractStatus] {
  val values = findValues

  case object PENDING_TRADING extends ContractStatus
  case object TRADING         extends ContractStatus
  case object PRE_DELIVERING  extends ContractStatus
  case object DELIVERING      extends ContractStatus
  case object DELIVERED       extends ContractStatus
  case object PRE_SETTLE      extends ContractStatus
  case object SETTLING        extends ContractStatus
  case object CLOSE           extends ContractStatus
}

sealed trait ContractType extends EnumEntry
object ContractType extends Enum[ContractType] with CirceEnum[ContractType] {
  val values = findValues

  case object PERPETUAL       extends ContractType
  case object CURRENT_MONTH   extends ContractType
  case object NEXT_MONTH      extends ContractType
  case object CURRENT_QUARTER extends ContractType
  case object NEXT_QUARTER    extends ContractType

}
