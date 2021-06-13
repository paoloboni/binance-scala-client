# Scala client for Binance API
<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-1-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

[![Build Status](https://github.com/paoloboni/binance-scala-client/actions/workflows/ci.yml/badge.svg)](https://github.com/paoloboni/binance-scala-client/actions?query=workflow)
[![Latest version](https://img.shields.io/maven-central/v/io.github.paoloboni/binance-scala-client_2.13.svg)](https://search.maven.org/artifact/io.github.paoloboni/binance-scala-client_2.13)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Join the chat at https://gitter.im/binance-scala-client/community](https://badges.gitter.im/binance-scala-client/community.svg)](https://gitter.im/binance-scala-client/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

A functional Scala client for Binance, powered by [cats-effect](https://typelevel.org/cats-effect/) 3.x.

This client is rate limited, based on [Binance API specification](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md).

## Getting started

If you use sbt add the following dependency to your build file:

```sbtshell
libraryDependencies += "io.github.paoloboni" %% "binance-scala-client" % "<version>"
```

## APIs supported

### Spot API

* [Exchange information](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#exchange-information): Current exchange trading rules and symbol information
* [Kline/Candlestick data](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#klinecandlestick-data): Kline/candlestick bars for a symbol
* [Symbol price ticker](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#symbol-price-ticker): Latest price for a symbol or symbols
* [Balance information](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#account-information-user_data): Current balance information
* [New order (trade)](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#new-order--trade): Send in a new order
* [Cancel order (trade)](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#cancel-order-trade): Cancel an active order
* [Cancel open orders (trade)](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#cancel-all-open-orders-on-a-symbol-trade): Cancels all active orders on a symbol

### Future API

* [Exchange information](https://binance-docs.github.io/apidocs/#exchange-information): Current exchange trading rules and symbol information
* [Kline/Candlestick data](https://binance-docs.github.io/apidocs/#kline-candlestick-data): Kline/candlestick bars for a symbol
* [Symbol price ticker](https://binance-docs.github.io/apidocs/#symbol-price-ticker): Latest price for a symbol or symbols
* [Account information](https://binance-docs.github.io/apidocs/#account-information-user_data): Get current account information
* [New order (trade)](https://binance-docs.github.io/apidocs/futures/en/#new-order-trade): Send in a new order
* [Change position mode (trade)](https://binance-docs.github.io/apidocs/futures/en/#change-position-mode-trade): Change user's position mode (Hedge Mode or One-way Mode ) on EVERY symbol
* [Change initial leverage (trade)](https://binance-docs.github.io/apidocs/futures/en/#change-initial-leverage-trade): Change user's initial leverage of specific symbol market

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
    
    BinanceClient.createSpotClient[IO](config)
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

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tr>
    <td align="center"><a href="https://github.com/DarkWingMcQuack"><img src="https://avatars.githubusercontent.com/u/38857302?v=4?s=100" width="100px;" alt=""/><br /><sub><b>DarkWingMcQuack</b></sub></a><br /><a href="https://github.com/paoloboni/binance-scala-client/commits?author=DarkWingMcQuack" title="Code">💻</a></td>
  </tr>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!