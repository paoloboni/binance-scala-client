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

package io.github.paoloboni.json

import io.circe.Decoder

case class Discriminator(name: String)

case class Perhaps[E](value: Option[E]):
  def fold[F](ifAbsent: => F)(ifPresent: E => F): F = value.fold(ifAbsent)(ifPresent)

object Perhaps:
  implicit def perhaps[E](implicit ev: E = null): Perhaps[E] = Perhaps(Option(ev))

trait SumDecoder[T] extends Decoder[T]

object SumDecoder:
  import scala.deriving._
  import scala.compiletime._
  import io.circe.DecodingFailure
  import io.circe.HCursor

  inline private def label[A]: String = constValue[A].asInstanceOf[String]

  inline def summonAllLabels[A <: Tuple]: List[String] =
    inline erasedValue[A] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => label[t] :: summonAllLabels[ts]

  inline def summonAllDecoders[A <: Tuple]: List[Decoder[_]] =
    inline erasedValue[A] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[Decoder[t]] :: summonAllDecoders[ts]

  inline given derived[T](using m: Mirror.Of[T], discriminator: Perhaps[Discriminator]): SumDecoder[T] =
    inline m match {
      case s: Mirror.SumOf[T] =>
        val labels   = summonAllLabels[s.MirroredElemLabels]
        val decoders = summonAllDecoders[s.MirroredElemTypes]
        new SumDecoder[T]() {
          def apply(c: HCursor): Decoder.Result[T] = {
            val discriminatorKey = discriminator.fold[String]("type")(_.name)
            c.downField(discriminatorKey).as[String] match {
              case Right(value) if (labels.contains(value)) =>
                (labels zip decoders).find(_._1 == value) match {
                  case Some((_, decoder)) => decoder.asInstanceOf[Decoder[T]].apply(c)
                  case _                  => Left(DecodingFailure("decoding failure", c.history))
                }
              case Right(unexpected) => Left(DecodingFailure(s"Unexpected filterType $unexpected", c.history))
              case Left(failure)     => Left(failure)
            }
          }
        }
      case _ => throw MatchError("only sum types are supported")
    }
