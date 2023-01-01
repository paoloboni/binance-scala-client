/*
 * Copyright (c) 2023 Paolo Boni
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

import enumeratum.EnumEntry
import shapeless.labelled.FieldType
import shapeless.{:+:, ::, CNil, Coproduct, HList, HNil, Inl, Inr, LabelledGeneric, Lazy, Witness}
import sttp.model.QueryParams

import java.time.Instant

trait StringConverter[T] {
  def to(t: T): String
}

object StringConverter {
  def apply[T: StringConverter]: StringConverter[T] = implicitly

  implicit def optionConverter[T: StringConverter]: StringConverter[Option[T]] =
    (obj: Option[T]) => obj.map(StringConverter[T].to).getOrElse("")

  implicit val stringConverter: StringConverter[String] = (obj: String) => obj

  implicit val intConverter: StringConverter[Int] = (obj: Int) => obj.toString

  implicit val longConverter: StringConverter[Long] = (obj: Long) => obj.toString

  implicit val booleanConverter: StringConverter[Boolean] = (obj: Boolean) => obj.toString

  implicit val bigDecimalConverter: StringConverter[BigDecimal] = (obj: BigDecimal) => obj.bigDecimal.toPlainString

  implicit val instantConverter: StringConverter[Instant] = (t: Instant) => t.toEpochMilli.toString

  implicit def enumEntryConverter[T <: EnumEntry]: StringConverter[T] = (obj: T) => obj.toString
}

trait QueryParamsConverter[T] {
  def to(t: T): QueryParams
}

object QueryParamsConverter {
  def apply[T: QueryParamsConverter]: QueryParamsConverter[T] = implicitly

  implicit val deriveHNil: QueryParamsConverter[HNil] = (_: HNil) => QueryParams()

  implicit def deriveHCons[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      scv: Lazy[StringConverter[H]],
      sct: QueryParamsConverter[T]
  ): QueryParamsConverter[FieldType[K, H] :: T] = new QueryParamsConverter[FieldType[K, H] :: T] {

    private val fieldName = witness.value.name

    override def to(hList: FieldType[K, H] :: T): QueryParams = hList match {
      case h :: t =>
        val qt = sct.to(t)
        scv.value.to(h) match {
          case "" => qt
          case _  => qt.param(fieldName, scv.value.to(h))
        }
    }
  }

  implicit val deriveCNil: QueryParamsConverter[CNil] = (t: CNil) => t.impossible

  implicit def deriveCoproduct[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hInstance: Lazy[QueryParamsConverter[H]],
      tInstance: QueryParamsConverter[T]
  ): QueryParamsConverter[FieldType[K, H] :+: T] = {
    case Inl(head) => hInstance.value.to(head).param("type", witness.value.name)
    case Inr(tail) => tInstance.to(tail)
  }

  implicit def deriveClass[A, Repr](implicit
      gen: LabelledGeneric.Aux[A, Repr],
      conv: Lazy[QueryParamsConverter[Repr]]
  ): QueryParamsConverter[A] = (a: A) => conv.map(_.to(gen.to(a))).value

  implicit class Ops[T](val t: T) extends AnyVal {
    def toQueryParams(implicit converter: QueryParamsConverter[T]): QueryParams = converter.to(t)
  }
}
