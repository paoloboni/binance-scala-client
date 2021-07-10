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

import io.github.paoloboni.http
import sttp.model.QueryParams

import scala.deriving._
import scala.compiletime._
import io.github.paoloboni.http.StringConverter

trait QueryParamsConverter[T]:
  def to(t: T): QueryParams

object QueryParamsConverter:
  def apply[T: QueryParamsConverter]: QueryParamsConverter[T] = summon

  inline private def label[A]: String = constValue[A].asInstanceOf[String]

  inline private def summonStringConverter[T]: StringConverter[T] = summonFrom {
    case converter: StringConverter[T] => converter
    case _ =>
      new StringConverter[T] {
        override def to(t: T): String = t.toString
      }
  }

  inline def processCases[NamesAndAlts <: Tuple](x: Any): QueryParams =
    inline erasedValue[NamesAndAlts] match {
      case _: (Tuple2[name, alt] *: alts1) =>
        x match {
          case a: alt => summonInline[QueryParamsConverter[alt]].to(a).param("type", label[name])
          case _      => processCases[alts1](x)
        }
      case _ => throw MatchError("failed to process case")
    }

  inline def deriveSum[T](s: Mirror.SumOf[T]): QueryParamsConverter[T] = new QueryParamsConverter[T] {
    override def to(t: T): QueryParams = processCases[Tuple.Zip[s.MirroredElemLabels, s.MirroredElemTypes]](t)
  }

  inline def summonStringConverters[A <: Tuple]: List[StringConverter[_]] =
    inline erasedValue[A] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonStringConverter[t] :: summonStringConverters[ts]

  inline given derived[T](using m: Mirror.Of[T]): QueryParamsConverter[T] = {
    inline m match {
      case s: Mirror.SumOf[T] => deriveSum[T](s)
      case p: Mirror.ProductOf[T] =>
        val converters = summonStringConverters[m.MirroredElemTypes]
        new QueryParamsConverter[T] {
          override def to(t: T): QueryParams = {
            val pElem = t.asInstanceOf[Product]
            (pElem.productElementNames.toList zip pElem.productIterator.toList zip converters)
              .map { case ((label, elem), converter) =>
                label -> converter.asInstanceOf[StringConverter[Any]].to(elem)
              }
              .foldRight(QueryParams()) {
                case ((label, value), params) if value != "" => params.param(label, value)
                case ((label, value), params)                => params
              }
          }
        }
    }
  }

  extension [T](t: T) def toQueryParams(using converter: QueryParamsConverter[T]): QueryParams = converter.to(t)
