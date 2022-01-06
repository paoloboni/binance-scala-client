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

package io.github.paoloboni.binance.fapi.response

import io.github.paoloboni.binance.common
import io.github.paoloboni.binance.fapi.FuturePositionSide

case class FutureAccountInfoResponse(
    feeTier: Int,
    canDeposit: Boolean,
    canTrade: Boolean,
    canWithdraw: Boolean,
    updateTime: Long,
    totalInitialMargin: BigDecimal,
    totalMaintMargin: BigDecimal,
    totalWalletBalance: BigDecimal,
    totalUnrealizedProfit: BigDecimal,
    totalMarginBalance: BigDecimal,
    totalPositionInitialMargin: BigDecimal,
    totalOpenOrderInitialMargin: BigDecimal,
    totalCrossWalletBalance: BigDecimal,
    totalCrossUnPnl: BigDecimal,
    availableBalance: BigDecimal,
    maxWithdrawAmount: BigDecimal,
    assets: List[OwnedAsset],
    positions: List[OpenPosition]
)

case class OwnedAsset(
    asset: String,
    walletBalance: BigDecimal,
    unrealizedProfit: BigDecimal,
    marginBalance: BigDecimal,
    maintMargin: BigDecimal,
    initialMargin: BigDecimal,
    positionInitialMargin: BigDecimal,
    openOrderInitialMargin: BigDecimal,
    crossWalletBalance: BigDecimal,
    crossUnPnl: BigDecimal,
    availableBalance: BigDecimal,
    maxWithdrawAmount: BigDecimal,
    marginAvailable: Boolean
)

case class OpenPosition(
    symbol: String,
    initialMargin: BigDecimal,
    maintMargin: BigDecimal,
    unrealizedProfit: BigDecimal,
    positionInitialMargin: BigDecimal,
    openOrderInitialMargin: BigDecimal,
    leverage: BigDecimal,
    isolated: Boolean,
    entryPrice: BigDecimal,
    maxNotional: BigDecimal,
    positionSide: FuturePositionSide,
    positionAmt: BigDecimal
)
