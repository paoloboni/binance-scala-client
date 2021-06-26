package io.github.paoloboni

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import io.github.paoloboni.binance.common.response.{DiffDepthStream, KLineStream}
import io.github.paoloboni.binance.common.{Interval, OrderSide, SpotConfig}
import io.github.paoloboni.binance.spot._
import io.github.paoloboni.binance.spot.parameters._
import io.github.paoloboni.binance.spot.response.{SpotAccountInfoResponse, SpotOrderCreateResponse}
import io.github.paoloboni.binance.{BinanceClient, _}
import org.scalatest.LoneElement
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.util.Random

class SpotE2ETests extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env with LoneElement {

  val config: SpotConfig = SpotConfig.Default(
    apiKey = sys.env("SPOT_API_KEY"),
    apiSecret = sys.env("SPOT_SECRET_KEY"),
    testnet = true
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
      .asserting(_ shouldBe a[SpotAccountInfoResponse])
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
          SpotOrderCreateParams.MARKET(
            symbol = "XRPUSDT",
            side = side,
            quantity = BigDecimal(100).some
          )
        )
      )
      .asserting(_ shouldBe a[SpotOrderCreateResponse])
  }

  "cancelOrder" in {
    BinanceClient
      .createSpotClient[IO](config)
      .use(client =>
        for {
          createOrderResponse <- client.createOrder(
            SpotOrderCreateParams.LIMIT(
              symbol = "XRPUSDT",
              side = OrderSide.SELL,
              timeInForce = SpotTimeInForce.GTC,
              quantity = 10,
              price = 1
            )
          )

          _ <- client.cancelOrder(
            SpotOrderCancelParams(
              symbol = "XRPUSDT",
              orderId = createOrderResponse.orderId.some,
              origClientOrderId = None
            )
          )
        } yield "OK"
      )
      .asserting(_ shouldBe "OK")
  }

  "cancelAllOrders" in {
    BinanceClient
      .createSpotClient[IO](config)
      .use(client =>
        for {
          _ <- client.createOrder(
            SpotOrderCreateParams.LIMIT(
              symbol = "XRPUSDT",
              side = OrderSide.SELL,
              timeInForce = SpotTimeInForce.GTC,
              quantity = 10,
              price = 1
            )
          )

          _ <- client.cancelAllOrders(
            SpotOrderCancelAllParams(symbol = "XRPUSDT")
          )
        } yield "OK"
      )
      .asserting(_ shouldBe "OK")
  }

  "kLineStreams" in {
    BinanceClient
      .createSpotClient[IO](config)
      .use(_.kLineStreams("btcusdt", Interval.`1m`).take(1).compile.toList)
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[KLineStream])
  }

  "diffDepthStream" in {
    BinanceClient
      .createSpotClient[IO](config)
      .use(_.diffDepthStream("btcusdt").take(1).compile.toList)
      .timeout(30.seconds)
      .asserting(_.loneElement shouldBe a[DiffDepthStream])
  }
}
