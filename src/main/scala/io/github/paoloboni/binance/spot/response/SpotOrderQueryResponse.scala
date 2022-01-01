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

import io.github.paoloboni.binance.common.OrderSide
import io.github.paoloboni.binance.spot.{SpotOrderStatus, SpotOrderType, SpotTimeInForce}

case class SpotOrderQueryResponse(
    symbol: String,
    orderId: Long,
    orderListId: Long,
    clientOrderId: String,
    price: BigDecimal,
    origQty: BigDecimal,
    executedQty: BigDecimal,
    cummulativeQuoteQty: BigDecimal,
    status: SpotOrderStatus,
    timeInForce: SpotTimeInForce,
    `type`: SpotOrderType,
    side: OrderSide,
    stopPrice: Option[BigDecimal] = None,
    icebergQty: Option[BigDecimal] = None,
    time: Long,
    updateTime: Long,
    isWorking: Boolean,
    origQuoteOrderQty: BigDecimal
)
