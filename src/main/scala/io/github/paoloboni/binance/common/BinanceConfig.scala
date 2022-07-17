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

package io.github.paoloboni.binance.common

import sttp.client3.UriContext
import sttp.model.Uri

import scala.concurrent.duration._

sealed trait BinanceConfig {
  def responseHeaderTimeout: Duration
  def maxTotalConnections: Int
  def rateLimiterBufferSize: Int
}

sealed trait FapiConfig extends BinanceConfig {

  def restBaseUrl: Uri
  def wsBaseUrl: Uri
  def exchangeInfoUrl: Uri
  def apiKey: String
  def apiSecret: String
  def recvWindow: Int
  def responseHeaderTimeout: Duration
  def maxTotalConnections: Int
  def rateLimiterBufferSize: Int
  def testnet: Boolean
}
object FapiConfig {
  final case class Default[F[_]](
      apiKey: String,
      apiSecret: String,
      recvWindow: Int = 5000,
      responseHeaderTimeout: Duration = 40.seconds,
      maxTotalConnections: Int = 20,
      rateLimiterBufferSize: Int = 1000,
      testnet: Boolean = false
  ) extends FapiConfig {
    lazy val restBaseUrl: Uri =
      if (testnet) uri"https://testnet.binancefuture.com"
      else uri"https://fapi.binance.com"
    lazy val wsBaseUrl: Uri =
      if (testnet) uri"wss://stream.binancefuture.com"
      else uri"wss://fstream.binance.com"
    lazy val exchangeInfoUrl: Uri = uri"$restBaseUrl/fapi/v1/exchangeInfo"
  }
  final case class Custom[F[_]](
      restBaseUrl: Uri,
      wsBaseUrl: Uri,
      exchangeInfoUrl: Uri,
      apiKey: String,
      apiSecret: String,
      recvWindow: Int = 5000,
      responseHeaderTimeout: Duration = 40.seconds,
      maxTotalConnections: Int = 20,
      rateLimiterBufferSize: Int = 1000,
      testnet: Boolean = false
  ) extends FapiConfig
}

sealed trait SpotConfig extends BinanceConfig {

  def restBaseUrl: Uri
  def wsBaseUrl: Uri
  def exchangeInfoUrl: Uri
  def apiKey: String
  def apiSecret: String
  def recvWindow: Int
  def responseHeaderTimeout: Duration
  def maxTotalConnections: Int
  def rateLimiterBufferSize: Int
  def testnet: Boolean
}
object SpotConfig {
  final case class Default[F[_]](
      apiKey: String,
      apiSecret: String,
      recvWindow: Int = 5000,
      responseHeaderTimeout: Duration = 40.seconds,
      maxTotalConnections: Int = 20,
      rateLimiterBufferSize: Int = 1000,
      testnet: Boolean = false
  ) extends SpotConfig {
    lazy val restBaseUrl: Uri =
      if (testnet) uri"https://testnet.binance.vision"
      else uri"https://api.binance.com"
    lazy val wsBaseUrl: Uri =
      if (testnet) uri"wss://testnet.binance.vision"
      else uri"wss://stream.binance.com:9443"
    lazy val exchangeInfoUrl: Uri = uri"$restBaseUrl/api/v3/exchangeInfo"
  }
  final case class Custom(
      restBaseUrl: Uri,
      wsBaseUrl: Uri,
      exchangeInfoUrl: Uri,
      apiKey: String,
      apiSecret: String,
      recvWindow: Int = 5000,
      responseHeaderTimeout: Duration = 40.seconds,
      maxTotalConnections: Int = 20,
      rateLimiterBufferSize: Int = 1000,
      testnet: Boolean = false
  ) extends SpotConfig
}
