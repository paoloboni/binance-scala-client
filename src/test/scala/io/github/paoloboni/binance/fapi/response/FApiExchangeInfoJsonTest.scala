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

package io.github.paoloboni.binance.fapi.response

import io.circe.parser.decode
import weaver.SimpleIOSuite

import scala.io.Source

object FApiExchangeInfoJsonTest extends SimpleIOSuite {

  pureTest("FApi ExchangeInfos should be decodeable from json") {
    val exchangeInfoStr = Source.fromResource("fapi/exchangeInfo.json").mkString
    val result          = decode[ExchangeInformation](exchangeInfoStr)
    assert(result.isRight)
  }

  pureTest("should ignore unsupported filters when decoding json") {
    val encoded =
      """
        |{
        |   "timezone":"UTC",
        |   "serverTime":1621582323258,
        |   "futuresType":"U_MARGINED",
        |   "rateLimits":[
        |
        |   ],
        |   "exchangeFilters":[
        |      {
        |         "filterType":"UNKNOWN"
        |      }
        |   ],
        |   "assets":[
        |      {
        |         "asset":"USDT",
        |         "marginAvailable":true,
        |         "autoAssetExchange":"0"
        |      },
        |      {
        |         "asset":"BTC",
        |         "marginAvailable":true,
        |         "autoAssetExchange":"0.00200000"
        |      }
        |   ],
        |   "symbols":[
        |      {
        |         "symbol":"BTCUSDT",
        |         "pair":"BTCUSDT",
        |         "contractType":"PERPETUAL",
        |         "deliveryDate":4133404800000,
        |         "onboardDate":1569398400000,
        |         "status":"TRADING",
        |         "maintMarginPercent":"2.5000",
        |         "requiredMarginPercent":"5.0000",
        |         "baseAsset":"BTC",
        |         "quoteAsset":"USDT",
        |         "marginAsset":"USDT",
        |         "pricePrecision":2,
        |         "quantityPrecision":3,
        |         "baseAssetPrecision":8,
        |         "quotePrecision":8,
        |         "underlyingType":"COIN",
        |         "underlyingSubType":[
        |            
        |         ],
        |         "settlePlan":0,
        |         "triggerProtect":"0.0500",
        |         "filters":[
        |            {
        |               "filterType":"UNKNOWN"
        |            }
        |         ],
        |         "orderTypes":[
        |            "LIMIT"
        |         ],
        |         "timeInForce":[
        |            "GTC"
        |         ]
        |      }
        |   ]
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
