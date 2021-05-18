package io.github.paoloboni

import io.github.paoloboni.binance.BinanceConfig

object TestConfig {
  val config: BinanceConfig = BinanceConfig(
    scheme = "https",
    host = "testnet.binance.vision",
    port = 443,
    infoUrl = "/api/v3/exchangeInfo",
    apiKey = sys.env("API_KEY"),
    apiSecret = sys.env("SECRET_KEY")
  )
}
