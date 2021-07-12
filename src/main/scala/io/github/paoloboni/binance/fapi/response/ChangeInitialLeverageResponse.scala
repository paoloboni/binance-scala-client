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

package io.github.paoloboni.binance.fapi.response

import io.circe.Decoder
import io.circe.generic.semiauto._
import io.github.paoloboni.binance.fapi._

case object INF

case class ChangeInitialLeverageResponse(
    symbol: String,
    leverage: Int,
    maxNotionalValue: MaxNotionalValue
)

sealed trait MaxNotionalValue
object MaxNotionalValue {
  case class Value(value: BigDecimal) extends MaxNotionalValue
  case object INF                     extends MaxNotionalValue
}

object ChangeInitialLeverageResponse {
  implicit val maxNotionalValueDecoder: Decoder[MaxNotionalValue] = Decoder.decodeString.flatMap {
    case "INF" => Decoder.const(MaxNotionalValue.INF)
    case _     => Decoder.decodeBigDecimal.map(MaxNotionalValue.Value.apply)
  }

  implicit val decoder: Decoder[ChangeInitialLeverageResponse] = deriveDecoder
}
