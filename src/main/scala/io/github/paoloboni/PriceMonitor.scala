/*
 * Copyright (c) 2020 Paolo Boni
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

package io.github.paoloboni

import _root_.io.github.paoloboni.binance.{BinanceClient, BinanceConfig}
import cats.effect.{ExitCode, IO, IOApp}
import fs2._
import log.effect.fs2.SyncLogWriter._

import scala.concurrent.duration._

object PriceMonitor extends IOApp {

  val config = BinanceConfig(
    scheme = "https",
    host = "api.binance.com",
    port = 443,
    infoUrl = "/api/v1/exchangeInfo",
    apiKey = "4zxX6rmIRmpRSVzwFatd5a8H8U0LmKybzdaphj6pRhFDD9mK029uUCDBsXtF5vDM",
    apiSecret = "WiOljwRePPXAm5Z9uf4ZLIp9VlcOprJ1Crpo1DbJ19AOf2EqW4McetQBEKmzCobV"
  )

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val log = consoleLog[IO]
    BinanceClient[IO](config)
      .use { client =>
        Stream
          .awakeEvery[IO](5.seconds)
          .repeat
          .evalMap(_ => client.getPrices())
          .evalMap(prices => log.info("Current prices: " + prices))
          .compile
          .drain
      }
      .redeem(
        { t =>
          log.error("Something went wrong", t)
          ExitCode(1)
        },
        _ => ExitCode.Success
      )
  }
}
