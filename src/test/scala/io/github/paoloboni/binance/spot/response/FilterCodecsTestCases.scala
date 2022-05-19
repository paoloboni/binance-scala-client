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

import io.github.paoloboni.binance.spot.response.FilterCodecsTest.TestCase

object FilterCodecsTestCases {
  implicit object PriceFilter extends TestCase[PRICE_FILTER] {
    val encoded               = """{"filterType":"PRICE_FILTER", "minPrice": 1, "maxPrice": 2, "tickSize": 3}"""
    val decoded: PRICE_FILTER = PRICE_FILTER(1, 2, 3)
  }
  implicit object PercentPrice extends TestCase[PERCENT_PRICE] {
    val encoded: String =
      """{"filterType": "PERCENT_PRICE", "multiplierUp": 1, "multiplierDown": 2, "avgPriceMins": 3}"""
    val decoded: PERCENT_PRICE = PERCENT_PRICE(1, 2, 3)
  }
  implicit object LotSize extends TestCase[LOT_SIZE] {
    val encoded: String   = """{"filterType": "LOT_SIZE", "minQty": 1, "maxQty": 2, "stepSize": 3}"""
    val decoded: LOT_SIZE = LOT_SIZE(1, 2, 3)
  }
  implicit object MarketLotSize extends TestCase[MARKET_LOT_SIZE] {
    val encoded: String          = """{"filterType": "MARKET_LOT_SIZE", "minQty": 1, "maxQty": 2, "stepSize": 3}"""
    val decoded: MARKET_LOT_SIZE = MARKET_LOT_SIZE(1, 2, 3)
  }
  implicit object MaxNumOrders extends TestCase[MAX_NUM_ORDERS] {
    val encoded: String         = """{"filterType": "MAX_NUM_ORDERS", "maxNumOrders": 1}"""
    val decoded: MAX_NUM_ORDERS = MAX_NUM_ORDERS(1)
  }
  implicit object MaxNumAlgoOrders extends TestCase[MAX_NUM_ALGO_ORDERS] {
    val encoded: String              = """{"filterType": "MAX_NUM_ALGO_ORDERS", "maxNumAlgoOrders": 1}"""
    val decoded: MAX_NUM_ALGO_ORDERS = MAX_NUM_ALGO_ORDERS(1)
  }
  implicit object MaxNumIcebergOrders extends TestCase[MAX_NUM_ICEBERG_ORDERS] {
    val encoded: String                 = """{"filterType": "MAX_NUM_ICEBERG_ORDERS", "maxNumIcebergOrders": 1}"""
    val decoded: MAX_NUM_ICEBERG_ORDERS = MAX_NUM_ICEBERG_ORDERS(1)
  }
  implicit object MinNotional extends TestCase[MIN_NOTIONAL] {
    val encoded: String =
      """{"filterType": "MIN_NOTIONAL", "minNotional": 1, "applyToMarket": true, "avgPriceMins": 3}"""
    val decoded: MIN_NOTIONAL = MIN_NOTIONAL(1, applyToMarket = true, 3)
  }
  implicit object IcebergParts extends TestCase[ICEBERG_PARTS] {
    val encoded: String        = """{"filterType": "ICEBERG_PARTS", "limit": 1}"""
    val decoded: ICEBERG_PARTS = ICEBERG_PARTS(1)
  }
  implicit object MaxPosition extends TestCase[MAX_POSITION] {
    val encoded: String       = """{"filterType": "MAX_POSITION", "maxPosition": 1}"""
    val decoded: MAX_POSITION = MAX_POSITION(1)
  }
  implicit object ExchangeMaxNumOrders extends TestCase[EXCHANGE_MAX_NUM_ORDERS] {
    val encoded: String                  = """{"filterType": "EXCHANGE_MAX_NUM_ORDERS", "maxNumOrders": 1}"""
    val decoded: EXCHANGE_MAX_NUM_ORDERS = EXCHANGE_MAX_NUM_ORDERS(1)
  }
  implicit object ExchangeMaxAlgoOrders extends TestCase[EXCHANGE_MAX_ALGO_ORDERS] {
    val encoded: String                   = """{"filterType": "EXCHANGE_MAX_ALGO_ORDERS", "maxNumAlgoOrders": 1}"""
    val decoded: EXCHANGE_MAX_ALGO_ORDERS = EXCHANGE_MAX_ALGO_ORDERS(1)
  }
  implicit object TrailingDelta extends TestCase[TRAILING_DELTA] {
    val encoded: String =
      """{"filterType": "TRAILING_DELTA","minTrailingAboveDelta": 1,"maxTrailingAboveDelta": 2,"minTrailingBelowDelta": 3,"maxTrailingBelowDelta": 4}""".stripMargin
    val decoded: TRAILING_DELTA = TRAILING_DELTA(1, 2, 3, 4)
  }
}
