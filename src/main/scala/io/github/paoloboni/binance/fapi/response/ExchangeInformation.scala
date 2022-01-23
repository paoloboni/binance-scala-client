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

import cats.effect.Async
import cats.syntax.all._
import io.circe.Decoder
import io.github.paoloboni.binance.common.response.RateLimit
import io.github.paoloboni.binance.fapi._
import io.github.paoloboni.http.ratelimit.{RateLimiter, RateLimiters}

sealed trait Filter

case class PRICE_FILTER(minPrice: BigDecimal, maxPrice: BigDecimal, tickSize: BigDecimal)              extends Filter
case class LOT_SIZE(minQty: BigDecimal, maxQty: BigDecimal, stepSize: BigDecimal)                      extends Filter
case class MARKET_LOT_SIZE(minQty: BigDecimal, maxQty: BigDecimal, stepSize: BigDecimal)               extends Filter
case class MAX_NUM_ORDERS(limit: Int)                                                                  extends Filter
case class MAX_NUM_ALGO_ORDERS(limit: Int)                                                             extends Filter
case class PERCENT_PRICE(multiplierUp: BigDecimal, multiplierDown: BigDecimal, multiplierDecimal: Int) extends Filter
case class MIN_NOTIONAL(notional: Int)                                                                 extends Filter

object Filter {
  implicit val decoder: Decoder[Filter] = FilterCodecs.decoder
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
    settlePlan: Long,
    triggerProtect: BigDecimal,
    orderTypes: List[FutureOrderType],
    filters: List[Filter],
    timeInForce: List[FutureTimeInForce]
)

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
  def createRateLimiters[F[_]: Async](rateLimiterBufferSize: Int): F[RateLimiters[F]] =
    rateLimits
      .map(_.toRate)
      .traverse(limit => RateLimiter.make[F](limit.perSecond, rateLimiterBufferSize, limit.limitType))
      .map(RateLimiters.apply)

}
