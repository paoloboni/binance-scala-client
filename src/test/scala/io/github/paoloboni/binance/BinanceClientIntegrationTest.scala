/*
 * Copyright (c) 2021 Paolo Boni
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.paoloboni.binance

import cats.Applicative
import cats.effect.{Clock, IO}
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import io.circe.parser._
import io.github.paoloboni.binance.Decoders._
import io.github.paoloboni.binance.Interval._
import io.github.paoloboni.integration._
import io.github.paoloboni.{Env, TestClient}
import log.effect.LogWriter
import log.effect.fs2.SyncLogWriter.log4sLog
import org.scalatest.{EitherValues, FreeSpec, Matchers, OptionValues}
import shapeless.tag

import java.time.Instant
import scala.concurrent.duration._

class BinanceClientIntegrationTest extends FreeSpec with Matchers with EitherValues with OptionValues with TestClient {

  "it should fire multiple requests when expected number of elements returned is above threshold" in new Env {
    withWiremockServer { server =>
      val from      = 1548806400000L
      val to        = 1548866280000L
      val symbol    = "ETHUSDT"
      val interval  = 1.minute.asBinanceInterval.value
      val threshold = 2

      stubInfoEndpoint(server)

      server.stubFor(
        get(s"/api/v1/klines?symbol=$symbol&interval=$interval&startTime=$from&endTime=$to&limit=$threshold")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806400000, "104.41000000", "104.43000000", "104.27000000", "104.37000000", "185.23745000", 1548806459999, "19328.98599530", 80, "62.03712000", "6475.81062590", "0"],
                          |  [1548806460000, "104.38000000", "104.40000000", "104.33000000", "104.36000000", "211.54271000", 1548806519999, "22076.70809650", 68, "175.75948000", "18342.53313250", "0"],
                          |  [1548806520000, "104.36000000", "104.39000000", "104.36000000", "104.38000000", "59.74736000", 1548806579999, "6235.56895740", 28, "37.98161000", "3963.95268370", "0"]
                          |]
              """.stripMargin)
          )
      )

      server.stubFor(
        get(s"/api/v1/klines?symbol=$symbol&interval=$interval&startTime=1548806520000&endTime=$to&limit=$threshold")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806520000, "104.36000000", "104.39000000", "104.36000000", "104.38000000", "59.74736000", 1548806579999, "6235.56895740", 28, "37.98161000", "3963.95268370", "0"],
                          |  [1548836340000, "105.13000000", "105.16000000", "105.07000000", "105.10000000", "201.06821000", 1548836399999, "21139.17349190", 55, "35.40525000", "3722.13452500", "0"],
                          |  [1548836400000, "105.13000000", "105.14000000", "105.05000000", "105.09000000", "70.72517000", 1548836459999, "7432.93828700", 45, "36.68194000", "3855.32695710", "0"]
                          |]
              """.stripMargin)
          )
      )

      // NOTE: the last element in this response has timestamp equal to `to` time (from query) minus 1 second, so no further query should be performed
      server.stubFor(
        get(s"/api/v1/klines?symbol=$symbol&interval=$interval&startTime=1548836400000&endTime=$to&limit=$threshold")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548836400000, "105.13000000", "105.14000000", "105.05000000", "105.09000000", "70.72517000", 1548836459999, "7432.93828700", 45, "36.68194000", "3855.32695710", "0"],
                          |  [1548866279000, "108.39000000", "108.39000000", "108.15000000", "108.22000000", "327.08359000", 1548866339999, "35415.40478090", 129, "163.42355000", "17699.38253540", "0"]
                          |]
              """.stripMargin)
          )
      )

      val config = prepareConfiguration(server)

      val result = BinanceClient(config, Clock[IO])
        .use { gw =>
          for {
            stream <- gw.getKLines(
              KLines(
                symbol = symbol,
                interval = interval.duration,
                startTime = Instant.ofEpochMilli(from),
                endTime = Instant.ofEpochMilli(to),
                limit = threshold
              )
            )
            list <- stream.compile.toList
          } yield list
        }
        .unsafeRunSync()

      val responseFullJson = parse(
        """
          |[
          |  [1548806400000, "104.41000000", "104.43000000", "104.27000000", "104.37000000", "185.23745000", 1548806459999, "19328.98599530", 80, "62.03712000", "6475.81062590", "0"],
          |  [1548806460000, "104.38000000", "104.40000000", "104.33000000", "104.36000000", "211.54271000", 1548806519999, "22076.70809650", 68, "175.75948000", "18342.53313250", "0"],
          |  [1548806520000, "104.36000000", "104.39000000", "104.36000000", "104.38000000", "59.74736000", 1548806579999, "6235.56895740", 28, "37.98161000", "3963.95268370", "0"],
          |  [1548836340000, "105.13000000", "105.16000000", "105.07000000", "105.10000000", "201.06821000", 1548836399999, "21139.17349190", 55, "35.40525000", "3722.13452500", "0"],
          |  [1548836400000, "105.13000000", "105.14000000", "105.05000000", "105.09000000", "70.72517000", 1548836459999, "7432.93828700", 45, "36.68194000", "3855.32695710", "0"],
          |  [1548866279000, "108.39000000", "108.39000000", "108.15000000", "108.22000000", "327.08359000", 1548866339999, "35415.40478090", 129, "163.42355000", "17699.38253540", "0"]
          |]
        """.stripMargin
      ).right.value
      val expected = responseFullJson.as[List[KLine]].right.value

      result should have size 6
      result should contain theSameElementsInOrderAs expected

      server.verify(3, getRequestedFor(urlMatching("/api/v1/klines.*")))
    }
  }

  "it should return a list of prices" in new Env {
    withWiremockServer { server =>
      stubInfoEndpoint(server)

      server.stubFor(
        get("/api/v3/ticker/price")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |    {
                          |        "symbol": "ETHBTC",
                          |        "price": "0.03444300"
                          |    },
                          |    {
                          |        "symbol": "LTCBTC",
                          |        "price": "0.01493000"
                          |    }
                          |]
                      """.stripMargin)
          )
      )

      val config = prepareConfiguration(server)

      val result = BinanceClient(config, Clock[IO])
        .use(_.getPrices())
        .unsafeRunSync()

      result should contain theSameElementsInOrderAs List(
        Price("ETHBTC", BigDecimal(0.03444300)),
        Price("LTCBTC", BigDecimal(0.01493000))
      )
    }
  }

  "it should return the balance" in withWiremockServer { server =>
    import Env.runtime
    import Env.log

    stubInfoEndpoint(server)

    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    server.stubFor(
      get(urlPathMatching("/api/v3/account"))
        .withHeader("X-MBX-APIKEY", equalTo(apiKey))
        .withQueryParam("recvWindow", equalTo("5000"))
        .withQueryParam("timestamp", equalTo(fixedTime.toString))
        .withQueryParam("signature", equalTo("82f4e72e95e63d666b6da651e82a701722ad8a785a169318d91f36f279c55821"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""
                        |{
                        |  "makerCommission": 15,
                        |  "takerCommission": 15,
                        |  "buyerCommission": 0,
                        |  "sellerCommission": 0,
                        |  "canTrade": true,
                        |  "canWithdraw": true,
                        |  "canDeposit": true,
                        |  "updateTime": 123456789,
                        |  "balances": [
                        |    {
                        |      "asset": "BTC",
                        |      "free": "4723846.89208129",
                        |      "locked": "0.00000000"
                        |    },
                        |    {
                        |      "asset": "LTC",
                        |      "free": "4763368.68006011",
                        |      "locked": "0.00000001"
                        |    }
                        |  ]
                        |}
                      """.stripMargin)
        )
    )

    val config = prepareConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)

    val result = BinanceClient(config, stubTimer(fixedTime))
      .use(_.getBalance())
      .unsafeRunSync()

    result shouldBe Map(
      tag[AssetTag]("BTC") -> Balance(BigDecimal("4723846.89208129"), BigDecimal("0.00000000")),
      tag[AssetTag]("LTC") -> Balance(BigDecimal("4763368.68006011"), BigDecimal("0.00000001"))
    )
  }

  "it should create an order" in withWiremockServer { server =>
    import Env.runtime
    import Env.log
    stubInfoEndpoint(server)

    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    server.stubFor(
      post(urlPathMatching("/api/v3/order"))
        .withHeader("X-MBX-APIKEY", equalTo(apiKey))
        .withRequestBody(containing("recvWindow=5000"))
        .withRequestBody(containing(s"timestamp=${fixedTime.toString}"))
        .withRequestBody(containing("signature=b3e7befe7367d8d8dcf3ef21ccbe37ff297995cf136b2bca21441f2f4b603883"))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withBody("""
                        |{
                        |  "symbol": "BTCUSDT",
                        |  "orderId": 28,
                        |  "clientOrderId": "6gCrw2kRUAF9CvJDGP16IP",
                        |  "transactTime": 1507725176595
                        |}
                      """.stripMargin)
        )
    )

    val config = prepareConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)

    val result = BinanceClient(config, stubTimer(fixedTime))
      .use(
        _.createOrder(
          OrderCreate(
            symbol = "BTCUSDT",
            side = OrderSide.BUY,
            `type` = OrderType.MARKET,
            timeInForce = None,
            quantity = 10.5,
            price = None,
            newClientOrderId = None,
            stopPrice = None,
            icebergQty = None,
            newOrderRespType = None
          )
        )
      )
      .unsafeRunSync()

    result shouldBe tag[OrderIdTag]("28")
  }

  private def stubInfoEndpoint(server: WireMockServer) = {
    server.stubFor(
      get("/api/v1/exchangeInfo")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""
                        |{
                        |  "rateLimits": [
                        |    {
                        |      "rateLimitType": "REQUEST_WEIGHT",
                        |      "interval": "MINUTE",
                        |      "intervalNum": 1,
                        |      "limit": 1200
                        |    }
                        |  ]
                        |}
                      """.stripMargin)
        )
    )
  }

  private def prepareConfiguration(server: WireMockServer, apiKey: String = "", apiSecret: String = "") =
    BinanceConfig("http", "localhost", server.port(), "/api/v1/exchangeInfo", apiKey, apiSecret)

  private def stubTimer(fixedTime: Long) = new Clock[IO] {
    override def applicative: Applicative[IO]  = ???
    override def monotonic: IO[FiniteDuration] = IO.pure(fixedTime.millis)
    override def realTime: IO[FiniteDuration]  = IO.pure(fixedTime.millis)
  }
}
