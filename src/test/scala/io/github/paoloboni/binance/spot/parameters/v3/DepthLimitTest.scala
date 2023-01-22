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

import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object DepthLimitTest extends SimpleIOSuite with Checkers {

  test("Limit 1-100") {
    forall(Gen.chooseNum(1, 100)) { value =>
      val limit = DepthLimit(value)
      expect.all(
        limit.value == value,
        DepthLimit.weight(limit) == 1
      )
    }
  }

  test("Limit 101-500") {
    forall(Gen.chooseNum(101, 500)) { value =>
      val limit = DepthLimit(value)
      expect.all(
        limit.value == value,
        DepthLimit.weight(limit) == 5
      )
    }
  }

  test("Limit 501-1000") {
    forall(Gen.chooseNum(501, 1000)) { value =>
      val limit = DepthLimit(value)
      expect.all(
        limit.value == value,
        DepthLimit.weight(limit) == 10
      )
    }
  }

  test("Limit 501-1000") {
    forall(Gen.chooseNum(1001, 5000)) { value =>
      val limit = DepthLimit(value)
      expect.all(
        limit.value == value,
        DepthLimit.weight(limit) == 50
      )
    }
  }

  test("Value <= 0") {
    forall(Gen.oneOf(Gen.negNum[Int], Gen.const(0))) { value =>
      val limit = DepthLimit(value)
      expect.all(
        limit.value == 1,
        DepthLimit.weight(limit) == 1
      )
    }
  }

  test("Value >= 5000") {
    forall(Gen.chooseNum(5001, Int.MaxValue)) { value =>
      val limit = DepthLimit(value)
      expect.all(
        limit.value == 5000,
        DepthLimit.weight(limit) == 50
      )
    }
  }
}
