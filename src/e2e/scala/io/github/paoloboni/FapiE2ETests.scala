package io.github.paoloboni

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import io.github.paoloboni.binance.common.{BinanceConfig, Interval, OrderId, OrderSide}
import io.github.paoloboni.binance.fapi._
import io.github.paoloboni.binance.fapi.response._
import io.github.paoloboni.binance.fapi.parameters._
import io.github.paoloboni.binance.{BinanceClient, _}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import cats.mtl.Ask._

import java.time.Instant
import scala.util.Random

class FapiE2ETests extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env {

  private val config: IO[BinanceConfig] = IO.pure(
    BinanceConfig(
      scheme = "https",
      host = "testnet.binancefuture.com",
      port = 443,
      infoUrl = "/fapi/v1/exchangeInfo",
      apiKey = sys.env("FAPI_API_KEY"),
      apiSecret = sys.env("FAPI_SECRET_KEY")
    )
  )

  "getPrices" in {
    config
      .flatMap(
        BinanceClient
          .createFutureClient[Eff]
          .use(_.getPrices())
          .run
      )
      .asserting(_ shouldNot be(empty))
  }

  "getBalance" in {
    config
      .flatMap(
        BinanceClient
          .createFutureClient[Eff]
          .use(_.getBalance())
          .run
      )
      .asserting(_ shouldBe a[FutureAccountInfoResponse])
  }

  "getKLines" in {
    val now = Instant.now()
    config
      .flatMap(
        BinanceClient
          .createFutureClient[Eff]
          .use(
            _.getKLines(
              common.parameters.KLines("BTCUSDT", Interval.`5m`, now.minusSeconds(3600), now, 100)
            ).compile.toList
          )
          .run
      )
      .asserting(_ shouldNot be(empty))
  }

  "createOrder" in {
    val side = Random.shuffle(OrderSide.values).head
    config
      .flatMap(
        BinanceClient
          .createFutureClient[Eff]
          .use(
            _.createOrder(
              FutureOrderCreateParams(
                symbol = "XRPUSDT",
                side = side,
                `type` = FutureOrderType.MARKET,
                timeInForce = None,
                quantity = 100,
                price = None,
                newClientOrderId = None,
                stopPrice = None,
                icebergQty = None,
                newOrderRespType = None
              )
            )
          )
          .run
      )
      .asserting(_ shouldBe a[OrderId])
  }
}
