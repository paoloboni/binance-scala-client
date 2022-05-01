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

import scala.io.Source

object SpotExchangeInfoJsonTest extends SimpleIOSuite {

  pureTest("Spot ExchangeInfos should be decodeable from json") {
    val exchangeInfoStr = Source.fromResource("spot/exchangeInfo.json").mkString
    val result          = decode[ExchangeInformation](exchangeInfoStr)
    assert(result.isRight)
  }

  pureTest("should ignore unsupported filters when decoding json") {
    val encoded =
      """
        |{
        |  "timezone": "UTC",
        |  "serverTime": 1650215125188,
        |  "rateLimits": [],
        |  "exchangeFilters": [],
        |  "symbols": [
        |    {
        |      "symbol": "BNBBUSD",
        |      "status": "TRADING",
        |      "baseAsset": "BNB",
        |      "baseAssetPrecision": 8,
        |      "quoteAsset": "BUSD",
        |      "quotePrecision": 8,
        |      "quoteAssetPrecision": 8,
        |      "baseCommissionPrecision": 8,
        |      "quoteCommissionPrecision": 8,
        |      "orderTypes": [
        |        "LIMIT",
        |        "LIMIT_MAKER",
        |        "MARKET",
        |        "STOP_LOSS_LIMIT",
        |        "TAKE_PROFIT_LIMIT"
        |      ],
        |      "icebergAllowed": true,
        |      "ocoAllowed": true,
        |      "quoteOrderQtyMarketAllowed": true,
        |      "allowTrailingStop": true,
        |      "isSpotTradingAllowed": true,
        |      "isMarginTradingAllowed": false,
        |      "filters": [
        |        {
        |          "filterType": "UNKNOWN"
        |        }
        |      ],
        |      "permissions": [
        |        "SPOT"
        |      ]
        |    }
        |  ]
        |}
        |""".stripMargin
    val result = decode[ExchangeInformation](encoded)
    assert.all(
      result.isRight,
      result.toOption.get.exchangeFilters.isEmpty,
      result.toOption.get.symbols.head.filters.isEmpty
    )
  }
}
