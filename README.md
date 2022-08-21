# Scala client for Binance API
<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-2-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

[![Build Status](https://github.com/paoloboni/binance-scala-client/actions/workflows/ci.yml/badge.svg)](https://github.com/paoloboni/binance-scala-client/actions?query=workflow)
[![Latest version](https://img.shields.io/maven-central/v/io.github.paoloboni/binance-scala-client_2.13.svg)](https://search.maven.org/artifact/io.github.paoloboni/binance-scala-client_2.13)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) 
[![Join the chat at https://gitter.im/binance-scala-client/community](https://badges.gitter.im/binance-scala-client/community.svg)](https://gitter.im/binance-scala-client/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Join the chat on Discord at https://discord.gg/7KrBehYs55](https://img.shields.io/discord/878591852331290634?logo=discord)](https://discord.gg/7KrBehYs55)
[![Support](https://img.shields.io/badge/support-buymeacoffee-green)](https://www.buymeacoffee.com/paoloboni)

A functional Scala client for Binance, powered by [cats-effect](https://typelevel.org/cats-effect/) and [fs2](https://fs2.io/).

This client is rate limited, based on [Binance API specification](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md).

## Version compatibility table

| JDK LTS version | Scala version                |
|-----------------|------------------------------|
| 11              | 2.12.x, 2.13.x, 3.0.x, 3.1.x |
| 17              | 2.12.x, 2.13.x, 3.0.x, 3.1.x |

## Getting started

If you use sbt add the following dependency to your build file:

```sbtshell
libraryDependencies += "io.github.paoloboni" %% "binance-scala-client" % "<version>"
```

## APIs supported

### Spot API

#### Rest
* [Exchange information](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#exchange-information): Current exchange trading rules and symbol information
* [Kline/Candlestick data](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#klinecandlestick-data): Kline/candlestick bars for a symbol
* [Symbol price ticker](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#symbol-price-ticker): Latest price for a symbol or symbols
* [Balance information](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#account-information-user_data): Current balance information
* [New order (trade)](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#new-order--trade): Send in a new order
* [Query order (trade)](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#query-order-user_data): Query status and fills of existing order
* [Cancel order (trade)](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#cancel-order-trade): Cancel an active order
* [Cancel open orders (trade)](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#cancel-all-open-orders-on-a-symbol-trade): Cancels all active orders on a symbol
* [Order book](https://binance-docs.github.io/apidocs/spot/en/#order-book): Order book depth on a symbol

#### WebSocket
* [Kline/Candlestick Streams](https://binance-docs.github.io/apidocs/spot/en/#kline-candlestick-streams): The Kline/Candlestick Stream push updates to the current klines/candlestick every two seconds.
* [Diff. Depth Stream](https://binance-docs.github.io/apidocs/spot/en/#diff-depth-stream): Order book price and quantity depth updates used to locally manage an order book.
* [All Book Tickers Stream](https://binance-docs.github.io/apidocs/spot/en/#all-book-tickers-stream): Pushes any update to the best bid or ask's price or quantity in real-time for all symbols.
* [Partial Book Depth Streams](https://binance-docs.github.io/apidocs/spot/en/#partial-book-depth-streams): Top bids and asks.
* [Trade Streams](https://binance-docs.github.io/apidocs/spot/en/#trade-streams): The Trade Streams push raw trade information.
* [Aggregate Trade Streams](https://binance-docs.github.io/apidocs/spot/en/#aggregate-trade-streams): The Aggregate Trade Streams push trade information that is aggregated for a single taker order.

### Future API

#### Rest
* [Exchange information](https://binance-docs.github.io/apidocs/futures/en/#exchange-information): Current exchange trading rules and symbol information
* [Kline/Candlestick data](https://binance-docs.github.io/apidocs/futures/en/#kline-candlestick-data): Kline/candlestick bars for a symbol
* [Symbol price ticker](https://binance-docs.github.io/apidocs/futures/en/#symbol-price-ticker): Latest price for a symbol or symbols
* [Account information](https://binance-docs.github.io/apidocs/#account-information-user_data): Get current account information
* [New order (trade)](https://binance-docs.github.io/apidocs/futures/en/#new-order-trade): Send in a new order
* [Query order](https://binance-docs.github.io/apidocs/futures/en/#query-order-user_data): Check an order's status
* [Cancel order](https://binance-docs.github.io/apidocs/futures/en/#cancel-order-trade): Cancel an active order
* [Cancel all open orders](https://binance-docs.github.io/apidocs/futures/en/#cancel-all-open-orders-trade): Cancel all open orders
* [Change position mode (trade)](https://binance-docs.github.io/apidocs/futures/en/#change-position-mode-trade): Change user's position mode (Hedge Mode or One-way Mode ) on EVERY symbol
* [Change initial leverage (trade)](https://binance-docs.github.io/apidocs/futures/en/#change-initial-leverage-trade): Change user's initial leverage of specific symbol market

#### WebSocket
* [Aggregate trade streams](https://binance-docs.github.io/apidocs/futures/en/#aggregate-trade-streams): The Aggregate Trade Streams push trade information that is aggregated for a single taker order every 100 milliseconds.
* [Kline/Candlestick Streams](https://binance-docs.github.io/apidocs/futures/en/#kline-candlestick-streams): The Kline/Candlestick Stream push updates to the current klines/candlestick every 250 milliseconds (if existing).
* [Mark Price Stream](https://binance-docs.github.io/apidocs/futures/en/#mark-price-stream): Mark price and funding rate for a single symbol pushed every 3 seconds.
* [Mark Price Stream for All market](https://binance-docs.github.io/apidocs/futures/en/#mark-price-stream-for-all-market): Mark price and funding rate for all symbols pushed every 3 seconds.
* [Continuous Contract kline streams](https://binance-docs.github.io/apidocs/futures/en/#continuous-contract-kline-candlestick-streams): Continuous Contract Kline/Candlestick Streams of updates every 250 milliseconds.

## Initialise the client

The client initialisation returns an API object as a [Cats Resource](https://typelevel.org/cats-effect/docs/std/resource).
There are two factories available at the moment, one for Spot API and the other one for Future API.

### Spot client

```scala
import cats.effect.{IO, Resource}
import io.github.paoloboni.binance.BinanceClient
import io.github.paoloboni.binance.common.SpotConfig
import io.github.paoloboni.binance.spot.SpotApi

val config = SpotConfig.Default(
  apiKey = "***",     // your api key
  apiSecret = "***"   // your api secret
)
val client: Resource[IO, SpotApi[IO]] = BinanceClient.createSpotClient[IO](config)
```

### Future client

```scala
import cats.effect.{IO, Resource}
import io.github.paoloboni.binance.BinanceClient
import io.github.paoloboni.binance.common.FapiConfig
import io.github.paoloboni.binance.fapi.FutureApi

val config = FapiConfig.Default(
  apiKey = "***",     // your api key
  apiSecret = "***"   // your api secret
)
val client: Resource[IO, FutureApi[IO]] = BinanceClient.createFutureClient[IO](config)
```

## Documentation

The API documentation is available [here](https://paoloboni.github.io/binance-scala-client/latest/api/).

## Example app

This is a sample app to monitor the exchange prices (fetch every 5 seconds).

```scala
import cats.effect.std.Console
import cats.effect.{IO, IOApp}
import fs2.Stream
import io.github.paoloboni.binance.BinanceClient
import io.github.paoloboni.binance.common.SpotConfig

import scala.concurrent.duration.DurationInt

object PriceMonitor extends IOApp.Simple {

  val config = SpotConfig.Default(
    apiKey = "***",
    apiSecret = "***"
  )

  override def run: IO[Unit] =
    BinanceClient
      .createSpotClient[IO](config)
      .use { client =>
        Stream
          .awakeEvery[IO](5.seconds)
          .repeat
          .evalMap(_ => client.V3.getPrices())
          .evalMap(prices => Console[IO].println("Current prices: " + prices))
          .compile
          .drain
      }
}
```

## Contributing

### How to run unit and integration tests

```
sbt test
```

### How to run end-to-end tests

```
FAPI_API_KEY=<your-fapi-api-key> \
    FAPI_SECRET_KEY=<your-fapi-secret-key> \
    SPOT_API_KEY=<your-spot-api-key> \
    SPOT_SECRET_KEY=<your-spot-secret-key> \
    sbt e2e:test
```

### How to get Spot API and secret keys

- Navigate to https://testnet.binance.vision
- Login with your Github account
- Click on "Generate HMAC_SHA256 Key"
- Enter a description and press "Generate"
- Take note of the generated values

### How to get Future API and secret keys

- Navigate to https://testnet.binancefuture.com
- Register a new account or login with an existing one
- Click on "API key" link in the lower part of the screen
- Take note of the values

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tr>
    <td align="center"><a href="https://github.com/DarkWingMcQuack"><img src="https://avatars.githubusercontent.com/u/38857302?v=4?s=100" width="100px;" alt=""/><br /><sub><b>DarkWingMcQuack</b></sub></a><br /><a href="https://github.com/paoloboni/binance-scala-client/commits?author=DarkWingMcQuack" title="Code">💻</a></td>
    <td align="center"><a href="https://github.com/Swoorup"><img src="https://avatars.githubusercontent.com/u/3408009?v=4?s=100" width="100px;" alt=""/><br /><sub><b>Swoorup Joshi</b></sub></a><br /><a href="https://github.com/paoloboni/binance-scala-client/commits?author=Swoorup" title="Code">💻</a></td>
  </tr>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!
