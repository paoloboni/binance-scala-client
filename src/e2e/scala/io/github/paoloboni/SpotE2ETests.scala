package io.github.paoloboni

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import io.github.paoloboni.binance.common.{BinanceConfig, Interval, OrderId, OrderSide}
import io.github.paoloboni.binance.spot._
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.common.parameters._
import io.github.paoloboni.binance.{BinanceClient, _}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.util.Random

class SpotE2ETests extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env {

  val config: BinanceConfig = BinanceConfig(
    scheme = "https",
    host = "testnet.binance.vision",
    port = 443,
    infoUrl = "/api/v3/exchangeInfo",
    apiKey = sys.env("SPOT_API_KEY"),
    apiSecret = sys.env("SPOT_SECRET_KEY")
  )

  "getPrices" in {
    BinanceClient
      .createSpotClient[IO](config)
      .use(_.getPrices())
      .asserting(_ shouldNot be(empty))
  }

  "getBalance" in {
    BinanceClient
      .createSpotClient[IO](config)
      .use(_.getBalance())
      .asserting(_ shouldNot be(empty))
  }

  "getKLines" in {
    val now = Instant.now()
    BinanceClient
      .createSpotClient[IO](config)
      .use(
        _.getKLines(common.parameters.KLines("BTCUSDT", Interval.`5m`, now.minusSeconds(3600), now, 100)).compile.toList
      )
      .asserting(_ shouldNot be(empty))
  }

  "createOrder" in {
    val side = Random.shuffle(OrderSide.values).head
    BinanceClient
      .createSpotClient[IO](config)
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
      .asserting(_ shouldBe a[OrderId])
  }

  "cancelOrder" in {
    BinanceClient
      .createSpotClient[IO](config)
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
        } yield ()
      )
      .redeem(
        _ => false,
        _ => true
      )
      .asserting(_ shouldBe true)
  }

  "cancelAllOrders" in {
    BinanceClient
      .createSpotClient[IO](config)
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
        } yield ()
      )
      .redeem(
        _ => false,
        _ => true
      )
      .asserting(_ shouldBe true)
  }
}
