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

case class Price(symbol: String, price: BigDecimal)

enum OrderSide derives EnumDecoder:
  case SELL, BUY

case class BinanceBalance(asset: String, free: BigDecimal, locked: BigDecimal)

trait EnumDecoder[T] extends Decoder[T]

object EnumDecoder:
  import scala.deriving._
  import scala.compiletime._
  import io.circe.HCursor

  inline private def label[A]: String = constValue[A].asInstanceOf[String]

  inline def summonAllLabels[A <: Tuple]: List[String] =
    inline erasedValue[A] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => label[t] :: summonAllLabels[ts]

  inline def summonAllValues[A <: Tuple]: List[Any] =
    inline erasedValue[A] match
      case _: EmptyTuple => Nil
      case _: (t *: ts) => summonInline[Mirror.ProductOf[t]].fromProduct(Tuple()).asInstanceOf[t] :: summonAllValues[ts]

  // only supports Enums
  inline given derived[T](using m: Mirror.Of[T]): EnumDecoder[T] = inline m match {
    case s: Mirror.SumOf[T] =>
      val labels = summonAllLabels[s.MirroredElemLabels]
      val values = summonAllValues[s.MirroredElemTypes]
      new EnumDecoder[T] {
        def apply(c: HCursor): Decoder.Result[T] = Decoder.decodeString
          .emap {
            case str if labels.contains(str) =>
              val ord = labels.zipWithIndex.toMap.apply(str)
              Right(values(ord).asInstanceOf[T])
            case str =>
              Left(s"Failed to decode $str")
          }
          .apply(c)
      }
    case _ => throw MatchError("only enums are supported")
  }
