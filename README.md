# Scala client for Binance API

[![Build Status](https://github.com/paoloboni/binance-scala-client/actions/workflows/ci.yml/badge.svg)](https://github.com/paoloboni/binance-scala-client/actions?query=workflow)
[![Latest version](https://img.shields.io/maven-central/v/io.github.paoloboni/binance-scala-client_2.13.svg)](https://search.maven.org/artifact/io.github.paoloboni/binance-scala-client_2.13)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Join the chat at https://gitter.im/binance-scala-client/community](https://badges.gitter.im/binance-scala-client/community.svg)](https://gitter.im/binance-scala-client/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

A functional Scala client for Binance.

This client is rate limited, based on [Binance API specification](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md).

---
**NOTE**

cats-effect 2.x is supported in v0.0.8 or older. Starting from v1.0.0 this project is using version 3.x of cats-effect. 

---

## Getting started

If you use sbt add the following dependency to your build file:

```sbtshell
libraryDependencies += "io.github.paoloboni" %% "binance-scala-client" % "<version>"
```

## APIs supported

* [Kline/Candlestick data](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#klinecandlestick-data): Kline/candlestick bars for a symbol
* [Symbol price ticker](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#symbol-price-ticker): Latest price for a symbol or symbols
* [Balance information](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#account-information-user_data): Current balance information
* [New order (trade)](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#new-order--trade): Send in a new order

## Example

This is a sample app to monitor the exchange prices (fetch every 5 seconds).

```scala
import cats.effect.kernel.Clock
import cats.effect.{ExitCode, IO, IOApp}
import io.github.paoloboni.binance.{BinanceClient, BinanceConfig}
import fs2._
import log.effect.fs2.SyncLogWriter._

import scala.concurrent.duration._

object PriceMonitor extends IOApp {

  val config = BinanceConfig(
    scheme = "https",
    host = "api.binance.com",
    port = 443,
    infoUrl = "/api/v3/exchangeInfo",
    apiKey = "***",
    apiSecret = "***"
  )

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val log                      = consoleLog[IO]
    implicit val withClock: WithClock[IO] = WithClock.create(Clock[IO])
    
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
```
