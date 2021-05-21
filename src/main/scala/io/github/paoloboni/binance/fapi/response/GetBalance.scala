package io.github.paoloboni.binance.fapi.response

import io.github.paoloboni.binance.common

case class GetBalance(
    assets: List[Asset],
    canDeposit: Boolean,
    canTrade: Boolean,
    canWithdraw: Boolean,
    feeTier: Int,
    maxWithdrawAmount: BigDecimal,
    totalInitialMargin: BigDecimal,
    totalMaintMargin: BigDecimal,
    totalMarginBalance: BigDecimal,
    totalOpenOrderInitialMargin: BigDecimal,
    totalPositionInitialMargin: BigDecimal,
    totalUnrealizedProfit: BigDecimal,
    totalWalletBalance: BigDecimal,
    updateTime: Long
)

case class Asset(
    asset: common.Asset,
    initialMargin: BigDecimal,
    maintMargin: BigDecimal,
    marginBalance: BigDecimal,
    maxWithdrawAmount: BigDecimal,
    openOrderInitialMargin: BigDecimal,
    positionInitialMargin: BigDecimal,
    unrealizedProfit: BigDecimal,
    walletBalance: BigDecimal
)
