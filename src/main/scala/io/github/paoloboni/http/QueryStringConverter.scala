/*
 * Copyright (c) 2019 Paolo Boni
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

import enumeratum.{Enum, EnumEntry}
import shapeless.labelled.{FieldType, field}
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}

import scala.util.{Failure, Success, Try}

trait QueryStringConverter[T] {
  def from(s: String): Try[T]
  def to(t: T): String
}

object QueryStringConverter {
  def apply[T: QueryStringConverter]: QueryStringConverter[T] = implicitly

  implicit val deriveHNil: QueryStringConverter[HNil] = new QueryStringConverter[HNil] {
    override def from(s: String): Try[HNil] = s match {
      case ""       => Success(HNil)
      case nonEmpty => Failure(new RuntimeException(s"Impossible to create HNil from '$nonEmpty'"))
    }
    override def to(t: HNil): String = ""
  }

  implicit def optionConverter[T: QueryStringConverter]: QueryStringConverter[Option[T]] =
    new QueryStringConverter[Option[T]] {
      def from(s: String): Try[Option[T]] = s match {
        case ""       => Success(None)
        case nonEmpty => QueryStringConverter[T].from(nonEmpty).map(Some(_))
      }
      def to(obj: Option[T]): String = obj.map(QueryStringConverter[T].to).getOrElse("")
    }

  implicit val stringConverter: QueryStringConverter[String] = new QueryStringConverter[String] {
    def from(s: String): Try[String] = Success(s)
    def to(obj: String): String      = obj
  }

  implicit val intConverter: QueryStringConverter[Int] = new QueryStringConverter[Int] {
    def from(s: String): Try[Int] = Try(s.toInt)
    def to(obj: Int): String      = obj.toString
  }

  implicit val longConverter: QueryStringConverter[Long] = new QueryStringConverter[Long] {
    def from(s: String): Try[Long] = Try(s.toLong)
    def to(obj: Long): String      = obj.toString
  }

  implicit val bigDecimalConverter: QueryStringConverter[BigDecimal] = new QueryStringConverter[BigDecimal] {
    def from(s: String): Try[BigDecimal] = Try(BigDecimal(s))
    def to(obj: BigDecimal): String      = obj.toString
  }

  def enumEntryConverter[T <: EnumEntry](enum: Enum[T]): QueryStringConverter[T] = new QueryStringConverter[T] {
    def from(s: String): Try[T] = Try(enum.withName(s))
    def to(obj: T): String      = obj.entryName
  }

  implicit def deriveHCons[K <: Symbol, H, T <: HList](
      implicit
      witness: Witness.Aux[K],
      scv: Lazy[QueryStringConverter[H]],
      sct: QueryStringConverter[T]
  ): QueryStringConverter[FieldType[K, H] :: T] = new QueryStringConverter[FieldType[K, H] :: T] {

    override def from(s: String): Try[FieldType[K, H] :: T] = s.span(_ != '&') match {
      case (before, after) =>
        val (_, value) = before.span(_ != '=')
        for {
          front <- scv.value.from(if (value.isEmpty) value else value.tail)
          back  <- sct.from(if (after.isEmpty) after else after.tail)
        } yield field[K](front) :: back
    }

    override def to(hList: FieldType[K, H] :: T): String = hList match {
      case h :: HNil => witness.value.name + "=" + scv.value.to(h)
      case h :: t    => witness.value.name + "=" + scv.value.to(h) + "&" + sct.to(t)
    }
  }

  implicit def deriveClass[A, Repr](
      implicit
      gen: LabelledGeneric.Aux[A, Repr],
      conv: Lazy[QueryStringConverter[Repr]]
  ): QueryStringConverter[A] = new QueryStringConverter[A] {

    def from(s: String): Try[A] = conv.map(_.from(s).map(gen.from)).value
    def to(a: A): String        = conv.map(_.to(gen.to(a))).value
  }
}
