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
package common

import io.circe.Decoder
import EnumDecoder._

case class Price(symbol: String, price: BigDecimal)

enum OrderSide:
  case SELL, BUY

object OrderSide:
  given decoder: Decoder[OrderSide] = EnumDecoder.derived

case class BinanceBalance(asset: String, free: BigDecimal, locked: BigDecimal)

object EnumDecoder:
  import scala.deriving._
  import scala.compiletime._

  inline private def label[A]: String = constValue[A].asInstanceOf[String]

  inline def processCases[NamesAndAlts <: Tuple](x: String): Either[String, Any] =
    inline erasedValue[NamesAndAlts] match {
      case _: (Tuple2[name, alt] *: alts1) =>
        if (x == label[name]) Right(summonInline[Mirror.ProductOf[alt]].fromProduct(Tuple()).asInstanceOf[alt])
        else processCases[alts1](x)
      case _ => Left(s"Failed to decode $x")
    }

  inline def derivedSum[T](s: Mirror.SumOf[T]): Decoder[T] =
    Decoder.decodeString.emap(
      processCases[Tuple.Zip[s.MirroredElemLabels, s.MirroredElemTypes]](_).map(_.asInstanceOf[T])
    )

  // only supports Enums
  inline given derived[T](using m: Mirror.Of[T]): Decoder[T] = inline m match {
    case s: Mirror.SumOf[T] => derivedSum[T](s)
    case _                  => throw MatchError("only enums are supported")
  }
