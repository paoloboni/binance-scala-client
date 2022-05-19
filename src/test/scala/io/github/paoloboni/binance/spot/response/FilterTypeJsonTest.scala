/*
 * Copyright (c) 2022 Paolo Boni
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

import io.circe.parser.decode
import weaver.SimpleIOSuite

object FilterTypeJsonTest extends SimpleIOSuite {

  pureTest("PERCENT_PRICE should be able to being decoded from json") {

    val actual = decode[Filter]("""
          |{
          |  "filterType": "PERCENT_PRICE",
          |  "multiplierUp": "1.1500",
          |  "multiplierDown": "0.8500",
          |  "avgPriceMins": 4
          |}
                   """.stripMargin)

    val expected = Right(
      PERCENT_PRICE(
        multiplierUp = 1.1500,
        multiplierDown = 0.8500,
        avgPriceMins = 4
      )
    )

    assert(actual == expected)
  }

  pureTest("MAX_NUM_ALGO_ORDERS should be able to being decoded from json") {

    val actual = decode[Filter]("""
          |{
          |  "filterType": "MAX_NUM_ALGO_ORDERS",
          |  "maxNumAlgoOrders": 100
          |}
                   """.stripMargin)

    val expected = Right(MAX_NUM_ALGO_ORDERS(100))

    assert(actual == expected)
  }

  pureTest("MAX_NUM_ORDERS should be able to being decoded from json") {

    val actual = decode[Filter]("""
          |{
          |  "filterType": "MAX_NUM_ORDERS",
          |  "maxNumOrders": 100
          |}
                   """.stripMargin)

    val expected = Right(MAX_NUM_ORDERS(100))

    assert(actual == expected)
  }
}
