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

package io.github.paoloboni.binance.common.response

import io.circe.Decoder
import io.github.paoloboni.binance.common.response.DiffDepthStream.{Ask, Bid}

import scala.util.Try

final case class DiffDepthStream(
    e: String,   // Event type
    E: Long,     // Event time
    s: String,   // Symbol
    U: Long,     // First update ID in event
    u: Long,     // Final update ID in event
    b: Seq[Bid], // Bids to be updated
    a: Seq[Ask]  // Asks to be updated
)

object DiffDepthStream {
  final case class Bid(price: BigDecimal, quantity: BigDecimal)
  final case class Ask(price: BigDecimal, quantity: BigDecimal)

  object IsBigDecimal {
    def unapply(arg: String): Option[BigDecimal] = Try(BigDecimal(arg)).toOption
  }

  implicit val bidDecoder: Decoder[Bid] =
    Decoder.decodeList[String].emap {
      case List(IsBigDecimal(price), IsBigDecimal(quantity)) =>
        Right(Bid(price, quantity))
      case other => Left("Invalid Bid: " + other)
    }

  implicit val askDecoder: Decoder[Ask] =
    Decoder.decodeList[String].emap {
      case List(IsBigDecimal(price), IsBigDecimal(quantity)) =>
        Right(Ask(price, quantity))
      case other => Left("Invalid Ask: " + other)
    }

  import io.circe.generic.semiauto._
  implicit val decoder: Decoder[DiffDepthStream] = deriveDecoder[DiffDepthStream]
}
