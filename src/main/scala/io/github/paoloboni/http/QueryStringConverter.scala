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
import eu.timepit.refined.api.{Refined, Validate}
import io.lemonlabs.uri.QueryString
import shapeless.labelled.FieldType
import shapeless.{:+:, ::, CNil, Coproduct, HList, HNil, Inl, Inr, LabelledGeneric, Lazy, Witness}

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

  implicit def enumEntryConverter[T <: EnumEntry](implicit enum: Enum[T]): StringConverter[T] = (obj: T) =>
    obj.entryName

  implicit def refinedConverter[T: StringConverter, P](implicit v: Validate[T, P]): StringConverter[T Refined P] =
    (t: Refined[T, P]) => StringConverter[T].to(t.value)
}

trait QueryStringConverter[T] {
  def to(t: T): QueryString
}

object QueryStringConverter {
  def apply[T: QueryStringConverter]: QueryStringConverter[T] = implicitly

  implicit val deriveHNil: QueryStringConverter[HNil] = (t: HNil) => QueryString.empty

  implicit def deriveHCons[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      scv: Lazy[StringConverter[H]],
      sct: QueryStringConverter[T]
  ): QueryStringConverter[FieldType[K, H] :: T] = new QueryStringConverter[FieldType[K, H] :: T] {

    private val fieldName = witness.value.name

    override def to(hList: FieldType[K, H] :: T): QueryString = hList match {
      case h :: t =>
        val qt = sct.to(t)
        scv.value.to(h) match {
          case "" => qt
          case _  => qt.addParam(fieldName, scv.value.to(h))
        }
    }
  }

  implicit val deriveCNil: QueryStringConverter[CNil] = (t: CNil) => t.impossible

  implicit def deriveCoproduct[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hInstance: Lazy[QueryStringConverter[H]],
      tInstance: QueryStringConverter[T]
  ): QueryStringConverter[FieldType[K, H] :+: T] = {
    case Inl(head) => hInstance.value.to(head).addParam("type" -> witness.value.name)
    case Inr(tail) => tInstance.to(tail)
  }

  implicit def deriveClass[A, Repr](implicit
      gen: LabelledGeneric.Aux[A, Repr],
      conv: Lazy[QueryStringConverter[Repr]]
  ): QueryStringConverter[A] = (a: A) => conv.map(_.to(gen.to(a))).value

  implicit class Ops[T](val t: T) extends AnyVal {
    def toQueryString(implicit converter: QueryStringConverter[T]): QueryString = converter.to(t)
  }
}
