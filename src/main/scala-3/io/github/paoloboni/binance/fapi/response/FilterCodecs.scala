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

import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.auto._

object FilterCodecs:
  val decoder: Decoder[Filter] = Decoder.decodeJson.flatMap { json =>
    val cursor = json.hcursor
    cursor.downField("filterType").as[String] match {
      case Right("PRICE_FILTER")        => Decoder[PRICE_FILTER].map(_.asInstanceOf[Filter])
      case Right("LOT_SIZE")            => Decoder[LOT_SIZE].map(_.asInstanceOf[Filter])
      case Right("MARKET_LOT_SIZE")     => Decoder[MARKET_LOT_SIZE].map(_.asInstanceOf[Filter])
      case Right("MAX_NUM_ORDERS")      => Decoder[MAX_NUM_ORDERS].map(_.asInstanceOf[Filter])
      case Right("MAX_NUM_ALGO_ORDERS") => Decoder[MAX_NUM_ALGO_ORDERS].map(_.asInstanceOf[Filter])
      case Right("PERCENT_PRICE")       => Decoder[PERCENT_PRICE].map(_.asInstanceOf[Filter])
      case Right("MIN_NOTIONAL")        => Decoder[MIN_NOTIONAL].map(_.asInstanceOf[Filter])
      case Right(unexpected) => Decoder.failed(DecodingFailure(s"Unexpected filterType $unexpected", cursor.history))
      case Left(failure)     => Decoder.failed(failure)
    }
  }
