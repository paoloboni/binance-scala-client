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

package io.github.paoloboni.http

import java.time.Instant
import scala.deriving._
import scala.compiletime._

trait StringConverter[T]:
  def to(t: T): String

object StringConverter:
  def apply[T: StringConverter]: StringConverter[T] = summon

  given optionConverter[T](using converter: StringConverter[T]): StringConverter[Option[T]] with
    def to(obj: Option[T]): String = obj.map(converter.to).getOrElse("")

  given StringConverter[String] with
    def to(obj: String): String = obj

  given StringConverter[Int] with
    def to(obj: Int): String = obj.toString

  given StringConverter[Long] with
    def to(obj: Long): String = obj.toString

  given StringConverter[Boolean] with
    def to(obj: Boolean): String = obj.toString

  given StringConverter[BigDecimal] with
    def to(obj: BigDecimal): String = obj.bigDecimal.toPlainString

  given StringConverter[Instant] with
    def to(t: Instant): String = t.toEpochMilli.toString

  inline private def label[A]: String = constValue[A].asInstanceOf[String]

  inline def processCases[NamesAndAlts <: Tuple](x: Any): String =
    inline erasedValue[NamesAndAlts] match {
      case _: (Tuple2[name, alt] *: alts1) =>
        x match {
          case a: alt => label[name]
          case _      => processCases[alts1](x)
        }
      case _ => throw MatchError("failed to process case")
    }

  inline def derivedSum[T](s: Mirror.SumOf[T]): StringConverter[T] = new StringConverter[T] {
    override def to(t: T): String = processCases[Tuple.Zip[s.MirroredElemLabels, s.MirroredElemTypes]](t)
  }

  // only supports Enums
  inline given derived[T](using m: Mirror.Of[T]): StringConverter[T] = inline m match {
    case s: Mirror.SumOf[T] => derivedSum[T](s)
    case _                  => throw MatchError("only enums are supported")
  }
