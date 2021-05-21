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

package io.github.paoloboni.binance.common.response

import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.github.paoloboni.http.ratelimit.Rate

import scala.concurrent.duration._

sealed trait RateLimitType extends EnumEntry
object RateLimitType extends Enum[RateLimitType] with CirceEnum[RateLimitType] {

  override def values = findValues

  case object REQUEST_WEIGHT extends RateLimitType
  case object ORDERS         extends RateLimitType
  case object RAW_REQUESTS   extends RateLimitType
  case object NONE           extends RateLimitType
}

sealed trait RateLimitInterval extends EnumEntry
object RateLimitInterval extends Enum[RateLimitInterval] with CirceEnum[RateLimitInterval] {

  override def values = findValues

  case object SECOND extends RateLimitInterval
  case object MINUTE extends RateLimitInterval
  case object DAY    extends RateLimitInterval
}

case class RateLimit(rateLimitType: RateLimitType, interval: RateLimitInterval, intervalNum: Int, limit: Int) {
  def toRate = Rate(
    limit,
    interval match {
      case RateLimitInterval.SECOND => intervalNum.seconds
      case RateLimitInterval.MINUTE => intervalNum.minutes
      case RateLimitInterval.DAY    => intervalNum.days
    },
    rateLimitType
  )
}

case class RateLimits(rateLimits: List[RateLimit])
