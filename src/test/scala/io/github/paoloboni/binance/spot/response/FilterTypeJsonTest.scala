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

package io.github.paoloboni.binance.common.response

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.decode
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._

class FilterJsonTests extends AnyFlatSpec with Matchers {

  "PERCENT_PRICE" should "be able to being decoded from json" in {

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

    actual shouldBe expected
  }

  "MAX_NUM_ALGO_ORDERS" should "be able to being decoded from json" in {

    val actual = decode[Filter]("""
                   |{
                   |  "filterType": "MAX_NUM_ALGO_ORDERS",
                   |  "limit": 100
                   |}
                   """.stripMargin)

    val expected = Right(MAX_NUM_ALGO_ORDERS(limit = 100))

    actual shouldBe expected
  }

  "MAX_NUM_ORDERS" should "be able to being decoded from json" in {

    val actual = decode[Filter]("""
                   |{
                   |  "filterType": "MAX_NUM_ORDERS",
                   |  "limit": 100
                   |}
                   """.stripMargin)

    val expected = Right(MAX_NUM_ORDERS(limit = 100))

    actual shouldBe expected
  }
}
