package io.github.paoloboni

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import io.github.paoloboni.binance.common.{BinanceConfig, Interval, OrderId, OrderSide}
import io.github.paoloboni.binance.spot._
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.{BinanceClient, _}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.util.Random

class SpotE2ETests extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env {

  private val config = IO.pure(
    BinanceConfig(
      scheme = "https",
      host = "testnet.binance.vision",
      port = 443,
      infoUrl = "/api/v3/exchangeInfo",
      apiKey = sys.env("SPOT_API_KEY"),
      apiSecret = sys.env("SPOT_SECRET_KEY")
    )
  )

  "getPrices" in {
    config
      .flatMap(
        BinanceClient
          .createSpotClient[Eff]
          .use(_.getPrices())
          .run
      )
      .asserting(_ shouldNot be(empty))
  }

  "getBalance" in {
    config
      .flatMap(
        BinanceClient
          .createSpotClient[Eff]
          .use(_.getBalance())
          .run
      )
      .asserting(_ shouldNot be(empty))
  }

  "getKLines" in {
    val now = Instant.now()
    config
      .flatMap(
        BinanceClient
          .createSpotClient[Eff]
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
          .createSpotClient[Eff]
          .use(
            _.createOrder(
              SpotOrderCreateParams(
                symbol = "XRPUSDT",
                side = side,
                `type` = SpotOrderType.MARKET,
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

  "cancelOrder" in {
    config
      .flatMap(
        BinanceClient
          .createSpotClient[Eff]
          .use(client =>
            for {
              id <- client.createOrder(
                SpotOrderCreateParams(
                  symbol = "XRPUSDT",
                  side = OrderSide.SELL,
                  `type` = SpotOrderType.LIMIT,
                  timeInForce = SpotTimeInForce.GTC.some,
                  quantity = 10,
                  price = BigDecimal(1).some,
                  newClientOrderId = None,
                  stopPrice = None,
                  icebergQty = None,
                  newOrderRespType = None
                )
              )

              _ <- client.cancelOrder(
                SpotOrderCancelParams(
                  symbol = "XRPUSDT",
                  orderId = id.some,
                  origClientOrderId = None
                )
              )
            } yield "OK"
          )
          .run
      )
      .asserting(_ shouldBe "OK")
  }

  "cancelAllOrders" in {
    config
      .flatMap(
        BinanceClient
          .createSpotClient[Eff]
          .use(client =>
            for {
              _ <- client.createOrder(
                SpotOrderCreateParams(
                  symbol = "XRPUSDT",
                  side = OrderSide.SELL,
                  `type` = SpotOrderType.LIMIT,
                  timeInForce = SpotTimeInForce.GTC.some,
                  quantity = 10,
                  price = BigDecimal(1).some,
                  newClientOrderId = None,
                  stopPrice = None,
                  icebergQty = None,
                  newOrderRespType = None
                )
              )

              _ <- client.cancelAllOrders(
                SpotOrderCancelAllParams(symbol = "XRPUSDT")
              )
            } yield "OK"
          )
          .run
      )
      .asserting(_ shouldBe "OK")
  }
}
