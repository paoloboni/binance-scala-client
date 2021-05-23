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

import io.github.paoloboni.http.QueryStringConverter.Ops
import io.lemonlabs.uri.QueryString
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class QueryStringConverterTest extends AnyFreeSpec with Matchers with TypeCheckedTripleEquals {

  "it should convert a query string to an object" in {

    case class TestClass(quantity: Int, price: BigDecimal, recvWindow: Int, timestamp: Long)

    val obj = TestClass(1, 0.1, 5000, 1499827319559L)

    val queryString = QueryString.parse("quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559")

    obj.toQueryString.paramMap should ===(queryString.paramMap)
  }

  "it should convert sum types" in {

    sealed trait Test
    case class Test1(quantity: Int, price: BigDecimal) extends Test
    case class Test2(recvWindow: Int, timestamp: Long) extends Test

    val obj1: Test = Test1(1, 0.1)
    val obj2: Test = Test2(5000, 1499827319559L)

    val q1 = QueryString.parse("quantity=1&price=0.1")
    val q2 = QueryString.parse("recvWindow=5000&timestamp=1499827319559")

    obj1.toQueryString.paramMap should ===(q1.paramMap)
    obj2.toQueryString.paramMap should ===(q2.paramMap)
  }
}
