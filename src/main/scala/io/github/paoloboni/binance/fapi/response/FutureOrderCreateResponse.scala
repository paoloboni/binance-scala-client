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

import io.github.paoloboni.binance.fapi.FuturePositionSide
import io.github.paoloboni.binance.common.OrderSide
import io.github.paoloboni.binance.fapi.FutureOrderStatus
import io.github.paoloboni.binance.fapi.FutureTimeInForce
import io.github.paoloboni.binance.fapi.parameters.FutureWorkingType
import io.github.paoloboni.binance.fapi.FutureOrderType

case class FutureOrderCreateResponse(
    clientOrderId: String,
    cumQty: BigDecimal,
    cumQuote: BigDecimal,
    executedQty: BigDecimal,
    orderId: Long,
    avgPrice: BigDecimal,
    origQty: BigDecimal,
    price: BigDecimal,
    reduceOnly: Boolean,
    side: OrderSide,
    positionSide: FuturePositionSide,
    status: FutureOrderStatus,
    stopPrice: BigDecimal,
    closePosition: Boolean,
    symbol: String,
    timeInForce: FutureTimeInForce,
    `type`: FutureOrderType,
    origType: FutureOrderType,
    activatePrice: Option[BigDecimal],
    priceRate: Option[BigDecimal],
    updateTime: Long,
    workingType: FutureWorkingType,
    priceProtect: Boolean
)
