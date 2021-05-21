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

package io.github.paoloboni.binance.fapi.response

import cats.effect.kernel.Temporal
import cats.implicits._
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.github.paoloboni.binance.fapi._
import io.github.paoloboni.binance.common.response.RateLimit
import io.github.paoloboni.http.ratelimit.RateLimiter
import enumeratum.{CirceEnum, Enum, EnumEntry}

sealed trait Filter

case class PRICE_FILTER(minPrice: BigDecimal, maxPrice: BigDecimal, tickSize: BigDecimal)              extends Filter
case class LOT_SIZE(minQty: BigDecimal, maxQty: BigDecimal, stepSize: BigDecimal)                      extends Filter
case class MARKET_LOT_SIZE(minQty: BigDecimal, maxQty: BigDecimal, stepSize: BigDecimal)               extends Filter
case class MAX_NUM_ORDERS(limit: Int)                                                                  extends Filter
case class MAX_NUM_ALGO_ORDERS(limit: Int)                                                             extends Filter
case class PERCENT_PRICE(multiplierUp: BigDecimal, multiplierDown: BigDecimal, multiplierDecimal: Int) extends Filter
case class MIN_NOTIONAL(notional: Int)                                                                 extends Filter

object Filter {
  implicit val genDevConfig: Configuration = Configuration.default.withDiscriminator("filterType")
  import io.circe.generic.extras.semiauto._

  implicit val decoder: Decoder[Filter] = deriveConfiguredDecoder[Filter]
}

sealed trait ContractType extends EnumEntry
object ContractType extends Enum[ContractType] with CirceEnum[ContractType] {
  val values = findValues

  case object PERPETUAL       extends ContractType
  case object CURRENT_MONTH   extends ContractType
  case object NEXT_MONTH      extends ContractType
  case object CURRENT_QUARTER extends ContractType
  case object NEXT_QUARTER    extends ContractType
}

case class Symbol(
    symbol: String,
    pair: String,
    contractType: String,
    deliveryDate: Long,
    onboardDate: Long,
    status: String,
    maintMarginPercent: BigDecimal,
    requiredMarginPercent: BigDecimal,
    baseAsset: String,
    quoteAsset: String,
    marginAsset: String,
    pricePrecision: Int,
    quantityPrecision: Int,
    baseAssetPrecision: Int,
    quotePrecision: Int,
    underlyingType: String,
    settlePlan: Int,
    triggerProtect: BigDecimal,
    orderTypes: List[OrderType],
    filters: List[Filter],
    timeInForce: List[TimeInForce]
) {
  def getContractType: Option[ContractType] = ContractType.withNameOption(contractType)
}

case class AssetInfo(asset: String, marginAvailable: Boolean, autoAssetExchange: BigDecimal)

case class ExchangeInformation(
    timezone: String,
    serverTime: Long,
    futuresType: String,
    rateLimits: List[RateLimit],
    exchangeFilters: List[Filter],
    assets: List[AssetInfo],
    symbols: List[Symbol]
) {
  def createRateLimiters[F[_]: Temporal](rateLimiterBufferSize: Int): F[List[RateLimiter[F]]] =
    rateLimits
      .map(_.toRate)
      .traverse(limit => RateLimiter.make[F](limit.perSecond, rateLimiterBufferSize, limit.limitType))

}
