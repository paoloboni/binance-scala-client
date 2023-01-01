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

package io.github.paoloboni.binance.spot.parameters.v3

import scala.annotation.nowarn

final case class DepthLimit private (value: Int) extends AnyVal {
  def copy(@nowarn value: Int): Unit = ()
}

object DepthLimit {
  private val maxValue = 5000
  private val minValue = 1

  def apply(value: Int): DepthLimit = {
    if (value < minValue) {
      new DepthLimit(minValue)
    } else if (value > maxValue) {
      new DepthLimit(maxValue)
    } else {
      new DepthLimit(value)
    }
  }

  def weight(limit: DepthLimit): Int =
    if (limit.value <= 100) 1
    else if (limit.value <= 500) 5
    else if (limit.value <= 1000) 10
    else 50

  val default: DepthLimit = DepthLimit(100)
}

final case class DepthParams(symbol: String, limit: Option[DepthLimit])
