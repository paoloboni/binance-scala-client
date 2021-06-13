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

package io.github.paoloboni.binance.common

import eu.timepit.refined.api.Refined
import eu.timepit.refined.{numeric, refineMV}
import io.github.paoloboni.binance.common.BinanceConfig.RecvWindow
import io.lemonlabs.uri.Url
import shapeless.{Witness => W}
import io.lemonlabs.uri.typesafe.dsl._

import scala.concurrent.duration._

sealed trait BinanceConfig {
  def responseHeaderTimeout: Duration
  def maxTotalConnections: Int
  def rateLimiterBufferSize: Int
}

object BinanceConfig {
  type RecvWindow = Int Refined numeric.Interval.Closed[W.`0`.T, W.`60000`.T]
}

sealed trait FapiConfig extends BinanceConfig {
  def restBaseUrl: Url
  def wsBaseUrl: Url
  def exchangeInfoUrl: Url
  def apiKey: String
  def apiSecret: String
  def recvWindow: RecvWindow
  def responseHeaderTimeout: Duration
  def maxTotalConnections: Int
  def rateLimiterBufferSize: Int
  def testnet: Boolean
}
object FapiConfig {
  final case class Default(
      apiKey: String,
      apiSecret: String,
      recvWindow: RecvWindow = refineMV(5000),
      responseHeaderTimeout: Duration = 40.seconds,
      maxTotalConnections: Int = 20,
      rateLimiterBufferSize: Int = 1000,
      testnet: Boolean = false
  ) extends FapiConfig {
    lazy val restBaseUrl: Url =
      if (testnet) Url.parse("https://testnet.binancefuture.com")
      else Url.parse("https://fapi.binance.com")
    lazy val wsBaseUrl: Url =
      if (testnet) Url.parse("wss://stream.binancefuture.com")
      else Url.parse("wss://fstream.binance.com")
    lazy val exchangeInfoUrl: Url = restBaseUrl / "fapi/v1/exchangeInfo"
  }
  final case class Custom(
      restBaseUrl: Url,
      wsBaseUrl: Url,
      exchangeInfoUrl: Url,
      apiKey: String,
      apiSecret: String,
      recvWindow: RecvWindow = refineMV(5000),
      responseHeaderTimeout: Duration = 40.seconds,
      maxTotalConnections: Int = 20,
      rateLimiterBufferSize: Int = 1000,
      testnet: Boolean = false
  ) extends FapiConfig
}

sealed trait SpotConfig extends BinanceConfig {
  def restBaseUrl: Url
  def exchangeInfoUrl: Url
  def apiKey: String
  def apiSecret: String
  def recvWindow: RecvWindow
  def responseHeaderTimeout: Duration
  def maxTotalConnections: Int
  def rateLimiterBufferSize: Int
  def testnet: Boolean
}
object SpotConfig {
  final case class Default(
      apiKey: String,
      apiSecret: String,
      recvWindow: RecvWindow = refineMV(5000),
      responseHeaderTimeout: Duration = 40.seconds,
      maxTotalConnections: Int = 20,
      rateLimiterBufferSize: Int = 1000,
      testnet: Boolean = false
  ) extends SpotConfig {
    lazy val restBaseUrl: Url =
      if (testnet) Url.parse("https://testnet.binance.vision")
      else Url.parse("https://api.binance.com")
    lazy val exchangeInfoUrl: Url = restBaseUrl / "api/v3/exchangeInfo"
  }
  final case class Custom(
      restBaseUrl: Url,
      exchangeInfoUrl: Url,
      apiKey: String,
      apiSecret: String,
      recvWindow: RecvWindow = refineMV(5000),
      responseHeaderTimeout: Duration = 40.seconds,
      maxTotalConnections: Int = 20,
      rateLimiterBufferSize: Int = 1000,
      testnet: Boolean = false
  ) extends SpotConfig
}
