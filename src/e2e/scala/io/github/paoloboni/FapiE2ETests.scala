package io.github.paoloboni

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import io.github.paoloboni.binance._
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.response.{ContractKLineStream, KLineStream}
import io.github.paoloboni.binance.fapi._
import io.github.paoloboni.binance.fapi.parameters._
import io.github.paoloboni.binance.fapi.response._
import org.scalatest.LoneElement
import org.scalatest.freespec.FixtureAsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import cats.implicits._
import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.util.Random

class FapiE2ETests
    extends FixtureAsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Env
    with LoneElement
    with CatsResourceIO[FutureApi[IO]] {

  val config: FapiConfig[IO] = FapiConfig.Default(
    apiKey = sys.env("FAPI_API_KEY"),
    apiSecret = sys.env("FAPI_SECRET_KEY"),
    testnet = true
  )

  val resource: Resource[IO, FutureApi[IO]] = BinanceClient.createFutureClient[IO](config)

  "getPrices" in { _.getPrices().asserting(_ shouldNot be(empty)) }

  "getPrice" in { _.getPrice(symbol = "BTCUSDT").asserting(_ shouldBe a[Price]) }

  "getBalance" in { _.getBalance().asserting(_ shouldBe a[FutureAccountInfoResponse]) }

  "getKLines" in { client =>
    val now = Instant.now()
    client
      .getKLines(common.parameters.KLines("BTCUSDT", Interval.`5m`, now.minusSeconds(3600), now, 100))
      .compile
      .toList
      .asserting(_ shouldBe a[List[_]])
  }

  "changePositionMode" ignore { _.changePositionMode(true).asserting(_ shouldBe ()) }

  "changeInitialLeverage" in {
    _.changeInitialLeverage(ChangeInitialLeverageParams(symbol = "BTCUSDT", leverage = 1))
      .asserting(x => (x.leverage, x.symbol) shouldBe (1, "BTCUSDT"))
  }

  "createOrder" in { client =>
    val side = Random.shuffle(OrderSide.values).head
    client
      .createOrder(
        FutureOrderCreateParams.MARKET(
          symbol = "LTCUSDT",
          side = side,
          positionSide = FuturePositionSide.BOTH,
          quantity = 10
        )
      )
      .asserting(_ shouldBe a[FutureOrderCreateResponse])
  }

  "getOrder" in { client =>
    val side = Random.shuffle(OrderSide.values).head
    val result = for {
      orderCreated <- client
        .createOrder(
          FutureOrderCreateParams.MARKET(
            symbol = "LTCUSDT",
            side = side,
            positionSide = FuturePositionSide.BOTH,
            quantity = 10
          )
        )
      orderFetched <- client
        .getOrder(
          FutureGetOrderParams.OrderId(
            symbol = "LTCUSDT",
            orderId = orderCreated.orderId
          )
        )
    } yield orderFetched
    result.asserting(_ shouldBe a[FutureOrderGetResponse])
  }

  "cancelOrder" in { client =>
    (for {
      createOrderResponse <- client.createOrder(
        FutureOrderCreateParams.STOP(
          symbol = "XRPUSDT",
          side = OrderSide.SELL,
          positionSide = FuturePositionSide.BOTH,
          timeInForce = FutureTimeInForce.GTC,
          quantity = 10,
          stopPrice = 1,
          price = 1.2
        )
      )

      _ <- client.cancelOrder(
        FutureOrderCancelParams(
          symbol = "XRPUSDT",
          orderId = createOrderResponse.orderId.some,
          origClientOrderId = None
        )
      )
    } yield "OK")
      .asserting(_ shouldBe "OK")
  }

  "cancelAllOrders" in { client =>
    (for {
      _ <- client.createOrder(
        FutureOrderCreateParams.LIMIT(
          symbol = "XRPUSDT",
          side = OrderSide.SELL,
          positionSide = FuturePositionSide.BOTH,
          timeInForce = FutureTimeInForce.GTC,
          quantity = 10,
          price = 1
        )
      )

      _ <- client.cancelAllOrders(
        FutureOrderCancelAllParams(symbol = "XRPUSDT")
      )
    } yield "OK")
      .asserting(_ shouldBe "OK")
  }

  "aggregateTradeStreams" in {
    _.aggregateTradeStreams("btcusdt")
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[AggregateTradeStream])
  }

  "kLineStreams" in {
    _.kLineStreams("btcusdt", Interval.`1m`)
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[KLineStream])
  }

  "contractKLineStreams" in {
    _.contractKLineStreams("btcusdt", FutureContractType.PERPETUAL, Interval.`1m`)
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[ContractKLineStream])
  }

  "markPriceStream" in {
    _.markPriceStream("btcusdt")
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[MarkPriceUpdate])
  }

  "markPriceStream all symbols" in {
    _.markPriceStream()
      .take(1)
      .compile
      .toList
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[MarkPriceUpdate])
  }
}
