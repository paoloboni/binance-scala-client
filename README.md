# Scala client for Binance API

[![Build Status](https://travis-ci.com/paoloboni/binance-scala-client.svg?branch=master)](https://travis-ci.com/paoloboni/binance-scala-client)
[![Latest version](https://img.shields.io/maven-central/v/io.github.paoloboni/binance-scala-client_2.13.svg)](https://search.maven.org/artifact/io.github.paoloboni/binance-scala-client_2.13)

A functional Scala client for Binance.

This client is rate limited, based on [Binance API specification](https://github.com/binance-exchange/binance-official-api-docs/blob/master/rest-api.md#limits).

## Getting started

If you use sbt add the following dependency to your build file:

```sbtshell
libraryDependencies += "io.github.paoloboni" %% "binance-scala-client" % "<version>"
```

## APIs supported

* [Kline/Candlestick data](https://github.com/binance-exchange/binance-official-api-docs/blob/master/rest-api.md#klinecandlestick-data): Kline/candlestick bars for a symbol
* [Symbol price ticker](https://github.com/binance-exchange/binance-official-api-docs/blob/master/rest-api.md#symbol-price-ticker): Latest price for a symbol or symbols
* [Balance information](https://github.com/binance-exchange/binance-official-api-docs/blob/master/rest-api.md#account-information-user_data): Current balance information
* [New order (trade)](https://github.com/binance-exchange/binance-official-api-docs/blob/master/rest-api.md#new-order--trade): Send in a new order

## Example

This is a sample app to monitor the exchange prices (fetch every 5 seconds).

```scala
import cats.effect.{ExitCode, IO, IOApp}
import fs2._
import log.effect.fs2.SyncLogWriter.log4sLog
import io.github.paoloboni.binance.{BinanceClient, BinanceConfig}

import scala.concurrent.duration._

object PriceMonitor extends IOApp {
  
  val config = BinanceConfig(
    scheme = "https",
    host = "api.binance.com",
    port = 443,
    infoUrl = "/api/v1/exchangeInfo",
    apiKey = "***",
    apiSecret = "***"
  )

  override def run(args: List[String]): IO[ExitCode] =
    log4sLog[IO]("logger")
      .flatMap { implicit log =>
        BinanceClient[IO](config)
          .use { client =>
            Stream
              .eval(client.getPrices())
              .repeat
              .zipLeft(Stream.fixedDelay(5.second))
              .map(prices => log.info("Current prices: " + prices))
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