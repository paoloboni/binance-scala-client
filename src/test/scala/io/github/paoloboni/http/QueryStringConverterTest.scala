/*
 * Copyright (c) 2020 Paolo Boni
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

import org.scalatest.{FreeSpec, Matchers}

import scala.util.Success

class QueryStringConverterTest extends FreeSpec with Matchers {

  "it should convert a query string to an object and viceversa" in {

    case class TestClass(quantity: Int, price: BigDecimal, recvWindow: Int, timestamp: Long)

    val obj = TestClass(1, 0.1, 5000, 1499827319559L)

    val queryString = "quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559"

    QueryStringConverter[TestClass].to(obj) shouldBe queryString
    QueryStringConverter[TestClass].from(queryString) shouldBe Success(obj)
  }
}
