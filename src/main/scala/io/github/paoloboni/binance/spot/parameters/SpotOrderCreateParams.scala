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

package io.github.paoloboni.binance.spot.parameters

import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.spot._

sealed trait SpotOrderCreateParams

object SpotOrderCreateParams {
  case class LIMIT(
      symbol: String,
      side: OrderSide,
      timeInForce: SpotTimeInForce,
      quantity: BigDecimal,
      price: BigDecimal,
      icebergQty: Option[BigDecimal] = None,
      newClientOrderId: Option[String] = None
  ) extends SpotOrderCreateParams

  case class MARKET(
      symbol: String,
      side: OrderSide,
      quantity: Option[BigDecimal],
      quoteOrderQty: Option[BigDecimal] = None,
      newClientOrderId: Option[String] = None
  ) extends SpotOrderCreateParams

  case class STOP_LOSS(
      symbol: String,
      side: OrderSide,
      quantity: BigDecimal,
      stopPrice: BigDecimal,
      newClientOrderId: Option[String] = None
  ) extends SpotOrderCreateParams

  case class STOP_LOSS_LIMIT(
      symbol: String,
      side: OrderSide,
      timeInForce: SpotTimeInForce,
      quantity: BigDecimal,
      price: BigDecimal,
      stopPrice: BigDecimal,
      icebergQty: Option[BigDecimal],
      newClientOrderId: Option[String] = None
  ) extends SpotOrderCreateParams

  case class TAKE_PROFIT(
      symbol: String,
      side: OrderSide,
      quantity: BigDecimal,
      stopPrice: BigDecimal,
      newClientOrderId: Option[String] = None
  ) extends SpotOrderCreateParams

  case class TAKE_PROFIT_LIMIT(
      symbol: String,
      side: OrderSide,
      timeInForce: SpotTimeInForce,
      quantity: BigDecimal,
      price: BigDecimal,
      stopPrice: BigDecimal,
      icebergQty: Option[BigDecimal],
      newClientOrderId: Option[String] = None
  ) extends SpotOrderCreateParams

  case class LIMIT_MAKER(
      symbol: String,
      side: OrderSide,
      timeInForce: SpotTimeInForce,
      quantity: BigDecimal,
      price: BigDecimal,
      icebergQty: Option[BigDecimal],
      newClientOrderId: Option[String] = None
  ) extends SpotOrderCreateParams
}
