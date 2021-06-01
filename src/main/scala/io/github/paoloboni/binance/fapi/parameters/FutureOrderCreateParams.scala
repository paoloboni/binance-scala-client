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

package io.github.paoloboni.binance.fapi.parameters

import io.github.paoloboni.binance.common.OrderSide
import io.github.paoloboni.binance.fapi._
import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.circe.generic.extras.Configuration
import io.circe.{Decoder, Encoder}

sealed trait FutureOrderCreateResponseType extends EnumEntry
object FutureOrderCreateResponseType
    extends Enum[FutureOrderCreateResponseType]
    with CirceEnum[FutureOrderCreateResponseType] {
  val values = findValues

  case object ACK    extends FutureOrderCreateResponseType
  case object RESULT extends FutureOrderCreateResponseType
}

sealed trait FutureOrderCreateParams

object FutureOrderCreateParams {

  case class LIMIT(
      symbol: String,
      side: OrderSide,
      positionSide: FuturePositionSide,
      timeInForce: FutureTimeInForce,
      quantity: BigDecimal,
      price: BigDecimal,
      newClientOrderId: Option[String] = None
  ) extends FutureOrderCreateParams

  case class MARKET(
      symbol: String,
      side: OrderSide,
      positionSide: FuturePositionSide,
      quantity: BigDecimal,
      newClientOrderId: Option[String] = None
  ) extends FutureOrderCreateParams

  case class STOP(
      symbol: String,
      side: OrderSide,
      positionSide: FuturePositionSide,
      timeInForce: FutureTimeInForce = FutureTimeInForce.GTC,
      quantity: BigDecimal,
      reduceOnly: Boolean = false,
      stopPrice: BigDecimal,
      workingType: FutureWorkingType = FutureWorkingType.CONTRACT_PRICE,
      priceProtect: Boolean = false,
      newClientOrderId: Option[String] = None
  ) extends FutureOrderCreateParams

  case class STOP_MARKET(
      symbol: String,
      side: OrderSide,
      positionSide: FuturePositionSide,
      stopPrice: BigDecimal,
      closePosition: Boolean,
      priceProtect: Boolean = false,
      reduceOnly: Boolean = false,
      newClientOrderId: Option[String] = None,
      workingType: FutureWorkingType = FutureWorkingType.CONTRACT_PRICE
  ) extends FutureOrderCreateParams

  case class TAKE_PROFIT(
      symbol: String,
      side: OrderSide,
      positionSide: FuturePositionSide,
      stopPrice: BigDecimal,
      quantity: BigDecimal,
      price: BigDecimal,
      reduceOnly: Boolean = false,
      timeInForce: FutureTimeInForce = FutureTimeInForce.GTC,
      newClientOrderId: Option[String] = None,
      workingType: FutureWorkingType = FutureWorkingType.CONTRACT_PRICE,
      priceProtect: Boolean = false
  ) extends FutureOrderCreateParams

  case class TAKE_PROFIT_MARKET(
      symbol: String,
      side: OrderSide,
      positionSide: FuturePositionSide,
      stopPrice: BigDecimal,
      reduceOnly: Boolean = false,
      newClientOrderId: Option[String] = None,
      closePosition: Boolean,
      priceProtect: Boolean = false,
      workingType: FutureWorkingType = FutureWorkingType.CONTRACT_PRICE
  ) extends FutureOrderCreateParams

  case class TRAILING_STOP_MARKET(
      symbol: String,
      side: OrderSide,
      callbackRate: BigDecimal,
      reduceOnly: Boolean = false,
      positionSide: FuturePositionSide,
      activationPrice: BigDecimal,
      quantity: BigDecimal,
      newClientOrderId: Option[String] = None
  ) extends FutureOrderCreateParams

  implicit val genDevConfig: Configuration = Configuration.default.withDiscriminator("type")
  import io.circe.generic.extras.semiauto._

  implicit val decoder: Decoder[FutureOrderCreateParams] = deriveConfiguredDecoder[FutureOrderCreateParams]
  implicit val encoder: Encoder[FutureOrderCreateParams] =
    deriveConfiguredEncoder[FutureOrderCreateParams]
      .mapJson(_.dropNullValues) // do not include None values in the json
}
