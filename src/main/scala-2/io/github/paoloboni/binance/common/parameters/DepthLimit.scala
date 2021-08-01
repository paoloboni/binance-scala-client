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

package io.github.paoloboni.binance.common.parameters

import scala.collection.immutable
import enumeratum.{CirceEnum, Enum, EnumEntry}

sealed abstract class DepthLimit(val weight: Int) extends EnumEntry
object DepthLimit extends Enum[DepthLimit] with CirceEnum[DepthLimit] {
  val values: immutable.IndexedSeq[DepthLimit] = findValues
  case object `5`    extends DepthLimit(1)
  case object `10`   extends DepthLimit(1)
  case object `20`   extends DepthLimit(1)
  case object `50`   extends DepthLimit(1)
  case object `100`  extends DepthLimit(1)
  case object `500`  extends DepthLimit(5)
  case object `1000` extends DepthLimit(10)
  case object `5000` extends DepthLimit(50)
}
