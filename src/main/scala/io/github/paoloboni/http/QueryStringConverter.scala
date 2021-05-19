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

import enumeratum.{Enum, EnumEntry}
import io.lemonlabs.uri.QueryString
import shapeless.labelled.{FieldType, field}
import shapeless.{::, HList, HNil, LabelledGeneric, Lazy, Witness}

import scala.util.{Failure, Success, Try}

trait StringConverter[T] {
  def from(s: String): Try[T]
  def to(t: T): String
}

object StringConverter {
  def apply[T: StringConverter]: StringConverter[T] = implicitly

  implicit def optionConverter[T: StringConverter]: StringConverter[Option[T]] =
    new StringConverter[Option[T]] {
      def from(s: String): Try[Option[T]] = s match {
        case ""       => Success(None)
        case nonEmpty => StringConverter[T].from(nonEmpty).map(Some(_))
      }
      def to(obj: Option[T]): String = obj.map(StringConverter[T].to).getOrElse("")
    }

  implicit val stringConverter: StringConverter[String] = new StringConverter[String] {
    def from(s: String): Try[String] = Success(s)
    def to(obj: String): String      = obj
  }

  implicit val intConverter: StringConverter[Int] = new StringConverter[Int] {
    def from(s: String): Try[Int] = Try(s.toInt)
    def to(obj: Int): String      = obj.toString
  }

  implicit val longConverter: StringConverter[Long] = new StringConverter[Long] {
    def from(s: String): Try[Long] = Try(s.toLong)
    def to(obj: Long): String      = obj.toString
  }

  implicit val bigDecimalConverter: StringConverter[BigDecimal] = new StringConverter[BigDecimal] {
    def from(s: String): Try[BigDecimal] = Try(BigDecimal(s))
    def to(obj: BigDecimal): String      = obj.bigDecimal.toPlainString
  }

  def enumEntryConverter[T <: EnumEntry](enum: Enum[T]): StringConverter[T] = new StringConverter[T] {
    def from(s: String): Try[T] = Try(enum.withName(s))
    def to(obj: T): String      = obj.entryName
  }
}

trait QueryStringConverter[T] {
  def from(s: QueryString): Try[T]
  def to(t: T): QueryString
}

object QueryStringConverter {
  def apply[T: QueryStringConverter]: QueryStringConverter[T] = implicitly

  implicit val deriveHNil: QueryStringConverter[HNil] = new QueryStringConverter[HNil] {
    override def from(s: QueryString): Try[HNil] = Success(HNil)
    override def to(t: HNil): QueryString        = QueryString.empty
  }

  implicit def deriveHCons[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      scv: Lazy[StringConverter[H]],
      sct: QueryStringConverter[T]
  ): QueryStringConverter[FieldType[K, H] :: T] = new QueryStringConverter[FieldType[K, H] :: T] {

    private val fieldName = witness.value.name

    override def from(s: QueryString): Try[FieldType[K, H] :: T] = {
      s.paramMap.get(fieldName) match {
        case Some(value) =>
          value match {
            case Vector(string) =>
              for {
                head <- scv.value.from(string)
                tail <- sct.from(s)
              } yield field[K](head) :: tail
            case v => Failure(new RuntimeException(s"failed to read value for key $fieldName in $v"))
          }
        case None => Failure(new RuntimeException(s"key not found: $fieldName"))
      }
    }

    override def to(hList: FieldType[K, H] :: T): QueryString = hList match {
      case h :: t =>
        val qt = sct.to(t)
        scv.value.to(h) match {
          case "" => qt
          case _  => qt.addParam(fieldName, scv.value.to(h))
        }
    }
  }

  implicit def deriveClass[A, Repr](implicit
      gen: LabelledGeneric.Aux[A, Repr],
      conv: Lazy[QueryStringConverter[Repr]]
  ): QueryStringConverter[A] = new QueryStringConverter[A] {
    def from(s: QueryString): Try[A] = conv.map(_.from(s).map(gen.from)).value
    def to(a: A): QueryString        = conv.map(_.to(gen.to(a))).value
  }
}
