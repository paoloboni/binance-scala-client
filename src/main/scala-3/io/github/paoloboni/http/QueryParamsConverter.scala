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

import sttp.model.QueryParams

import scala.deriving._
import scala.compiletime._

trait QueryParamsConverter[T]:
  def to(t: T): QueryParams

object QueryParamsConverter:
  def apply[T: QueryParamsConverter]: QueryParamsConverter[T] = summon

  inline private def label[A]: String = constValue[A].asInstanceOf[String]

  inline def processElems[NamesAndElems <: Tuple](n: Int)(x: Any): QueryParams =
    inline erasedValue[NamesAndElems] match {
      case _: (Tuple2[name, elem] *: elems1) =>
        val e: elem = x.asInstanceOf[Product].productElement(n).asInstanceOf[elem]
        summonInline[StringConverter[elem]].to(e) match {
          case ""        => processElems[elems1](n + 1)(x)
          case converted => processElems[elems1](n + 1)(x).param(label[name], converted)
        }
      case _ => QueryParams()
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

  inline def derivedProduct[T](p: Mirror.ProductOf[T]): QueryParamsConverter[T] = new QueryParamsConverter[T] {
    override def to(t: T): QueryParams = processElems[Tuple.Zip[p.MirroredElemLabels, p.MirroredElemTypes]](0)(t)
  }

  inline given derived[T](using m: Mirror.Of[T]): QueryParamsConverter[T] = inline m match {
    case s: Mirror.SumOf[T]     => deriveSum[T](s)
    case p: Mirror.ProductOf[T] => derivedProduct[T](p)
  }

  extension [T](t: T) def toQueryParams(using converter: QueryParamsConverter[T]): QueryParams = converter.to(t)
