package io.github.paoloboni.binance.fapi.parameters

import io.github.paoloboni.binance.common.parameters._

case class OrderCreation(
    symbol: String,
    side: OrderSide,
    `type`: OrderType,
    timeInForce: Option[TimeInForce],
    quantity: BigDecimal,
    price: Option[BigDecimal],
    newClientOrderId: Option[String],
    stopPrice: Option[BigDecimal],
    icebergQty: Option[BigDecimal],
    newOrderRespType: Option[OrderCreateResponseType]
)
