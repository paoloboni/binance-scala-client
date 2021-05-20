package io.github.paoloboni

import cats.effect.IO
import cats.implicits._
import cats.effect.testing.scalatest.AsyncIOSpec
import io.github.paoloboni.TestConfig.config
import io.github.paoloboni.binance.spot._
import io.github.paoloboni.binance.common.parameters._
import io.github.paoloboni.binance.BinanceClient
import io.github.paoloboni.binance.common._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.util.Random

class E2ETests extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env {

  "getPrices" in {
    BinanceClient[IO](config)
      .use(_.getPrices())
      .asserting(_ shouldNot be(empty))
  }

  "getBalance" in {
    BinanceClient[IO](config)
      .use(_.getBalance())
      .asserting(_ shouldNot be(empty))
  }

  "getKLines" in {
    val now = Instant.now()
    BinanceClient[IO](config)
      .use(_.getKLines(KLineParameters("BTCUSDT", 5.minutes, now.minusSeconds(3600), now, 100)).compile.toList)
      .asserting(_ shouldNot be(empty))
  }

  "createOrder" in {
    val side = Random.shuffle(OrderSide.values).head
    BinanceClient[IO](config)
      .use(
        _.createOrder(
          OrderCreationParameters(
            symbol = "XRPUSDT",
            side = side,
            `type` = OrderType.MARKET,
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
    BinanceClient[IO](config)
      .use(client =>
        for {
          id <- client.createOrder(
            OrderCreationParameters(
              symbol = "XRPUSDT",
              side = OrderSide.SELL,
              `type` = OrderType.LIMIT,
              timeInForce = TimeInForce.GTC.some,
              quantity = 10,
              price = BigDecimal(1).some,
              newClientOrderId = None,
              stopPrice = None,
              icebergQty = None,
              newOrderRespType = None
            )
          )

          _ <- client.cancelOrder(
            OrderCancelParameters(
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
    BinanceClient[IO](config)
      .use(client =>
        for {
          _ <- client.createOrder(
            OrderCreationParameters(
              symbol = "XRPUSDT",
              side = OrderSide.SELL,
              `type` = OrderType.LIMIT,
              timeInForce = TimeInForce.GTC.some,
              quantity = 10,
              price = BigDecimal(1).some,
              newClientOrderId = None,
              stopPrice = None,
              icebergQty = None,
              newOrderRespType = None
            )
          )

          _ <- client.cancelAllOrders(
            OrderCancelAllParameters(symbol = "XRPUSDT")
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
