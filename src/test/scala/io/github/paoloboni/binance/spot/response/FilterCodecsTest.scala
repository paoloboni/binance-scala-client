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

package io.github.paoloboni.binance.spot.response

import weaver.{Expectations, SimpleIOSuite}
import io.circe.parser.decode

object FilterCodecsTest extends SimpleIOSuite {

  import FilterCodecsTestCases._

  pureTest("decode PRICE_FILTER")(verify[PRICE_FILTER])
  pureTest("decode PERCENT_PRICE")(verify[PERCENT_PRICE])
  pureTest("decode LOT_SIZE")(verify[LOT_SIZE])
  pureTest("decode MARKET_LOT_SIZE")(verify[MARKET_LOT_SIZE])
  pureTest("decode MAX_NUM_ORDERS")(verify[MAX_NUM_ORDERS])
  pureTest("decode MAX_NUM_ALGO_ORDERS")(verify[MAX_NUM_ALGO_ORDERS])
  pureTest("decode MAX_NUM_ICEBERG_ORDERS")(verify[MAX_NUM_ICEBERG_ORDERS])
  pureTest("decode MIN_NOTIONAL")(verify[MIN_NOTIONAL])
  pureTest("decode ICEBERG_PARTS")(verify[ICEBERG_PARTS])
  pureTest("decode MAX_POSITION")(verify[MAX_POSITION])
  pureTest("decode EXCHANGE_MAX_NUM_ORDERS")(verify[EXCHANGE_MAX_NUM_ORDERS])
  pureTest("decode EXCHANGE_MAX_ALGO_ORDERS")(verify[EXCHANGE_MAX_ALGO_ORDERS])
  pureTest("decode TRAILING_DELTA")(verify[TRAILING_DELTA])

  trait TestCase[T] {
    def encoded: String
    def decoded: T
  }
  object TestCase {
    def apply[T](implicit ev: TestCase[T]): TestCase[T] = ev
  }

  private def verify[T <: Filter: TestCase]: Expectations = {
    expect(decode[Filter](TestCase[T].encoded) == Right(TestCase[T].decoded))
  }
}
