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

package io.github.paoloboni.binance.fapi.response

import org.scalatest.flatspec.AnyFlatSpec
import io.circe.generic.auto._
import org.scalatest.matchers.should.Matchers
import io.circe.parser.decode
import io.circe.parser
import io.circe.syntax._
import scala.io.Source

class FApiExchangeInfoJsonTests extends AnyFlatSpec with Matchers {
  val exchangeInfoTestPath =
    System.getProperty("user.dir") + "/src/test/scala/io/github/paoloboni/binance/fapi/response/exchangeInfo.json"

  val exchangeInfoStr = Source.fromFile(exchangeInfoTestPath).mkString

  "FApi ExchangeInfos" should "be decodeable from json" in {
    val result = decode[ExchangeInformation](exchangeInfoStr)

    result.left.map(println(_))
    result.isRight shouldBe true

  }
}
