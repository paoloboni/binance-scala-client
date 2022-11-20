/*
 * Copyright (c) 2022 Paolo Boni
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

import cats.effect.{ExitCode, IO}
import cats.syntax.all._
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import fs2.Stream
import io.circe.parser._
import io.github.paoloboni.TestAsync
import io.github.paoloboni.binance.common._
import io.github.paoloboni.binance.common.response.{
  Ask,
  Bid,
  ContractKLineStream,
  ContractKLineStreamPayload,
  KLineStream,
  KLineStreamPayload,
  Level
}
import io.github.paoloboni.binance.fapi._
import io.github.paoloboni.binance.fapi.parameters._
import io.github.paoloboni.binance.fapi.response._
import org.http4s.websocket.WebSocketFrame
import scodec.bits.ByteVector
import sttp.client3.UriContext
import weaver.GlobalRead

import java.time.Instant
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class FapiClientIntegrationTest(global: GlobalRead) extends IntegrationTest(global) {

  integrationTest("it should fire multiple requests when expected number of elements returned is above threshold") {
    case WebServer(server, _) =>
      val from      = 1548806400000L
      val to        = 1548866280000L
      val symbol    = "ETHUSDT"
      val interval  = Interval.`1m`
      val threshold = 2

      val stubResponse1 = IO.delay(
        server.stubFor(
          get(urlPathEqualTo("/fapi/v1/klines"))
            .withQueryParams(
              Map(
                "symbol"    -> equalTo(symbol),
                "interval"  -> equalTo(interval.toString),
                "startTime" -> equalTo(from.toString),
                "endTime"   -> equalTo(to.toString),
                "limit"     -> equalTo(threshold.toString)
              ).asJava
            )
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
      )

      val stubResponse2 = IO.delay(
        server.stubFor(
          get(urlPathEqualTo("/fapi/v1/klines"))
            .withQueryParams(
              Map(
                "symbol"    -> equalTo(symbol),
                "interval"  -> equalTo(interval.toString),
                "startTime" -> equalTo("1548806520000"),
                "endTime"   -> equalTo(to.toString),
                "limit"     -> equalTo(threshold.toString)
              ).asJava
            )
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
      )

      // NOTE: the last element in this response has timestamp equal to `to` time (from query) minus 1 second, so no further query should be performed
      val stubResponse3 = IO.delay(
        server.stubFor(
          get(urlPathEqualTo("/fapi/v1/klines"))
            .withQueryParams(
              Map(
                "symbol"    -> equalTo(symbol),
                "interval"  -> equalTo(interval.toString),
                "startTime" -> equalTo("1548836400000"),
                "endTime"   -> equalTo(to.toString),
                "limit"     -> equalTo(threshold.toString)
              ).asJava
            )
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
      )

      (for {
        _      <- stubResponse1
        _      <- stubResponse2
        _      <- stubResponse3
        _      <- stubInfoEndpoint(server)
        config <- createConfiguration(server)
        result <- BinanceClient
          .createFutureClient[IO](config)
          .use { gw =>
            gw
              .getKLines(
                common.parameters.KLines(
                  symbol = symbol,
                  interval = interval,
                  startTime = Instant.ofEpochMilli(from),
                  endTime = Instant.ofEpochMilli(to),
                  limit = threshold
                )
              )
              .compile
              .toList
          }
        _ <- IO.delay(server.verify(3, getRequestedFor(urlMatching("/fapi/v1/klines.*"))))
      } yield result).map { result =>
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
        ).toOption.get
        val expected = responseFullJson.as[List[KLine]].toOption.get

        expect(result == expected)
      }
  }

  integrationTest("it should return the orderbook depth") { case WebServer(server, _) =>
    val symbol = "ETHUSDT"
    val limit  = DepthLimit.`5`

    val stubResponse = IO.delay(
      server.stubFor(
        get(urlPathEqualTo("/fapi/v1/depth"))
          .withQueryParams(
            Map(
              "symbol" -> equalTo(symbol),
              "limit"  -> equalTo(limit.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |{
                          |  "lastUpdateId": 1027024,
                          |  "E": 1589436922972,
                          |  "T": 1589436922959,
                          |  "bids": [
                          |    [
                          |      "4.00000000",
                          |      "431.00000000"
                          |    ]
                          |  ],
                          |  "asks": [
                          |    [
                          |      "4.00000200",
                          |      "12.00000000"
                          |    ]
                          |  ]
                          |}
                      """.stripMargin)
          )
      )
    )

    for {
      _      <- stubInfoEndpoint(server)
      _      <- stubResponse
      config <- createConfiguration(server)
      result <- BinanceClient
        .createFutureClient[IO](config)
        .use { gw =>
          gw.getDepth(DepthParams(symbol, limit))
        }
      expected = DepthGetResponse(
        lastUpdateId = 1027024,
        E = 1589436922972L,
        T = 1589436922959L,
        bids = List(
          Bid(4.00000000, 431.0)
        ),
        asks = List(
          Ask(4.00000200, 12.0)
        )
      )
    } yield expect(result == expected)
  }

  integrationTest("it should be able to stream klines even with a threshold of 1") { case WebServer(server, _) =>
    val from      = 1548806400000L
    val to        = 1548806640000L
    val symbol    = "ETHUSDT"
    val interval  = Interval.`1m`
    val threshold = 1

    val stubResponse1 = IO.delay(
      server.stubFor(
        get(urlPathEqualTo("/fapi/v1/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo(from.toString),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806400000, "104.41000000", "104.43000000", "104.27000000", "104.37000000", "185.23745000", 1548806459999, "19328.98599530", 80, "62.03712000", "6475.81062590", "0"]
                          |]
              """.stripMargin)
          )
      )
    )

    val stubResponse2 = IO.delay(
      server.stubFor(
        get(urlPathEqualTo("/fapi/v1/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548806459999"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806460000, "104.38000000", "104.40000000", "104.33000000", "104.36000000", "211.54271000", 1548806519999, "22076.70809650", 68, "175.75948000", "18342.53313250", "0"]
                          |]
              """.stripMargin)
          )
      )
    )

    val stubResponse3 = IO.delay(
      server.stubFor(
        get(urlPathEqualTo("/fapi/v1/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548806519999"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806520000, "104.36000000", "104.39000000", "104.36000000", "104.38000000", "59.74736000", 1548806579999, "6235.56895740", 28, "37.98161000", "3963.95268370", "0"]
                          |]
              """.stripMargin)
          )
      )
    )

    // NOTE: the last element in this response has timestamp equal to `to` time (from query) minus 1 second, so no further query should be performed
    val stubResponse4 = IO.delay(
      server.stubFor(
        get(urlPathEqualTo("/fapi/v1/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548806579999"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806580000,"104.37000000","104.37000000","104.11000000","104.30000000","503.86391000",1548806639999,"52516.17118740",150,"275.42894000","28709.15114540","0"]
                          |]
              """.stripMargin)
          )
      )
    )

    val stubResponse5 = IO.delay(
      server.stubFor(
        get(urlPathEqualTo("/fapi/v1/klines"))
          .withQueryParams(
            Map(
              "symbol"    -> equalTo(symbol),
              "interval"  -> equalTo(interval.toString),
              "startTime" -> equalTo("1548806639999"),
              "endTime"   -> equalTo(to.toString),
              "limit"     -> equalTo(threshold.toString)
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                          |[
                          |  [1548806640000,"104.30000000","104.35000000","104.19000000","104.27000000","251.83113000",1548806699999,"26262.34369560",93,"102.24293000","10663.18343790","0"]
                          |]
              """.stripMargin)
          )
      )
    )

    (for {
      _      <- stubResponse1
      _      <- stubResponse2
      _      <- stubResponse3
      _      <- stubResponse4
      _      <- stubResponse5
      _      <- stubInfoEndpoint(server)
      config <- createConfiguration(server)
      result <- BinanceClient
        .createFutureClient[IO](config)
        .use { gw =>
          gw
            .getKLines(
              common.parameters.KLines(
                symbol = symbol,
                interval = interval,
                startTime = Instant.ofEpochMilli(from),
                endTime = Instant.ofEpochMilli(to),
                limit = threshold
              )
            )
            .compile
            .toList
        }
      _ <- IO.delay(server.verify(4, getRequestedFor(urlMatching("/fapi/v1/klines.*"))))
    } yield result).map { result =>
      val responseFullJson = parse(
        """
          |[
          | [1548806400000,"104.41000000","104.43000000","104.27000000","104.37000000","185.23745000",1548806459999,"19328.98599530",80,"62.03712000","6475.81062590","0"],
          | [1548806460000,"104.38000000","104.40000000","104.33000000","104.36000000","211.54271000",1548806519999,"22076.70809650",68,"175.75948000","18342.53313250","0"],
          | [1548806520000,"104.36000000","104.39000000","104.36000000","104.38000000","59.74736000",1548806579999,"6235.56895740",28,"37.98161000","3963.95268370","0"],
          | [1548806580000,"104.37000000","104.37000000","104.11000000","104.30000000","503.86391000",1548806639999,"52516.17118740",150,"275.42894000","28709.15114540","0"]
          |]
        """.stripMargin
      ).toOption.get
      val expected = responseFullJson.as[List[KLine]].toOption.get

      expect(result == expected)
    }

  }

  integrationTest("it should return a list of prices") { case WebServer(server, _) =>
    val stubResponse = IO.delay(
      server.stubFor(
        get("/fapi/v1/ticker/price")
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
                          |]""".stripMargin)
          )
      )
    )

    for {
      _      <- stubInfoEndpoint(server)
      _      <- stubResponse
      config <- createConfiguration(server)
      result <- BinanceClient
        .createFutureClient[IO](config)
        .use(_.getPrices())
    } yield expect(
      result == List(
        Price("ETHBTC", BigDecimal(0.03444300)),
        Price("LTCBTC", BigDecimal(0.01493000))
      )
    )

  }

  integrationTest("it should return a single price") { case WebServer(server, _) =>
    for {
      _ <- stubInfoEndpoint(server)
      _ <- IO.delay(
        server.stubFor(
          get(urlPathEqualTo("/fapi/v1/ticker/price"))
            .withQueryParam("symbol", equalTo("ETHBTC"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody("""
                          |{
                          |    "symbol": "ETHBTC",
                          |    "price": "0.03444300"
                          |}""".stripMargin)
            )
        )
      )
      config <- createConfiguration(server)
      result <- BinanceClient
        .createFutureClient[IO](config)
        .use(_.getPrice("ETHBTC"))
    } yield expect(result == Price("ETHBTC", BigDecimal(0.03444300)))
  }

  integrationTest("it should return the balance") { case WebServer(server, _) =>
    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    val responseBody =
      """
        |{
        |    "feeTier": 0,
        |    "canTrade": true,
        |    "canDeposit": true,
        |    "canWithdraw": true,
        |    "updateTime": 0,
        |    "totalInitialMargin": "0.00000000",
        |    "totalMaintMargin": "0.00000000",
        |    "totalWalletBalance": "23.72469206",
        |    "totalUnrealizedProfit": "0.00000000",
        |    "totalMarginBalance": "23.72469206",
        |    "totalPositionInitialMargin": "0.00000000",
        |    "totalOpenOrderInitialMargin": "0.00000000",
        |    "totalCrossWalletBalance": "23.72469206",
        |    "totalCrossUnPnl": "0.00000000",
        |    "availableBalance": "23.72469206",
        |    "maxWithdrawAmount": "23.72469206",
        |    "assets": [
        |        {
        |            "asset": "USDT",
        |            "walletBalance": "23.72469206",
        |            "unrealizedProfit": "0.00000000",
        |            "marginBalance": "23.72469206",
        |            "maintMargin": "0.00000000",
        |            "initialMargin": "0.00000000",
        |            "positionInitialMargin": "0.00000000",
        |            "openOrderInitialMargin": "0.00000000",
        |            "crossWalletBalance": "23.72469206",
        |            "crossUnPnl": "0.00000000",
        |            "availableBalance": "23.72469206",
        |            "maxWithdrawAmount": "23.72469206",
        |            "marginAvailable": true
        |        }
        |    ],
        |    "positions": [
        |        {
        |            "symbol": "BTCUSDT",
        |            "initialMargin": "0",
        |            "maintMargin": "0",
        |            "unrealizedProfit": "0.00000000",
        |            "positionInitialMargin": "0",
        |            "openOrderInitialMargin": "0",
        |            "leverage": "100",
        |            "isolated": true,
        |            "entryPrice": "0.00000",
        |            "maxNotional": "250000",
        |            "positionSide": "BOTH",
        |            "positionAmt": "0"
        |        }
        |    ]
        |}""".stripMargin

    val expected = FutureAccountInfoResponse(
      feeTier = 0,
      canTrade = true,
      canDeposit = true,
      canWithdraw = true,
      updateTime = 0L,
      totalInitialMargin = 0,
      totalMaintMargin = 0,
      totalWalletBalance = 23.72469206,
      totalUnrealizedProfit = 0.00000000,
      totalMarginBalance = 23.72469206,
      totalPositionInitialMargin = 0.00000000,
      totalOpenOrderInitialMargin = 0.00000000,
      totalCrossWalletBalance = 23.72469206,
      totalCrossUnPnl = 0.00000000,
      availableBalance = 23.72469206,
      maxWithdrawAmount = 23.72469206,
      assets = List(
        OwnedAsset(
          asset = "USDT",
          walletBalance = 23.72469206,
          unrealizedProfit = 0.00000000,
          marginBalance = 23.72469206,
          maintMargin = 0.00000000,
          initialMargin = 0.00000000,
          positionInitialMargin = 0.00000000,
          openOrderInitialMargin = 0.00000000,
          crossWalletBalance = 23.72469206,
          crossUnPnl = 0.00000000,
          availableBalance = 23.72469206,
          maxWithdrawAmount = 23.72469206,
          marginAvailable = true
        )
      ),
      positions = List(
        OpenPosition(
          symbol = "BTCUSDT",
          initialMargin = 0,
          maintMargin = 0,
          unrealizedProfit = 0.00000000,
          positionInitialMargin = 0,
          openOrderInitialMargin = 0,
          leverage = 100,
          isolated = true,
          entryPrice = 0.00000,
          maxNotional = 250000,
          positionSide = FuturePositionSide.BOTH,
          positionAmt = 0
        )
      )
    )

    val stubResponse = IO.delay(
      server.stubFor(
        get(urlPathMatching("/fapi/v1/account"))
          .withHeader("X-MBX-APIKEY", equalTo(apiKey))
          .withQueryParam("recvWindow", equalTo("5000"))
          .withQueryParam("timestamp", equalTo(fixedTime.toString))
          .withQueryParam("signature", equalTo("6cd35332399b004466463b9ad65a112a14f31fb9ddfd5e19bd7298fbd491dbc7"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(responseBody)
          )
      )
    )

    IO.pure(new TestAsync(onRealtime = fixedTime.millis)).flatMap { implicit async =>
      for {
        _      <- stubInfoEndpoint(server)
        _      <- stubResponse
        config <- createConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)
        result <- BinanceClient
          .createFutureClient[IO](config)
          .use(_.getBalance())
      } yield expect(result == expected)
    }
  }

  integrationTest("it should be able to change the position mode") { case WebServer(server, _) =>
    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    val stubResponse = IO.delay(
      server.stubFor(
        post(urlPathMatching("/fapi/v1/positionSide/dual"))
          .withHeader("X-MBX-APIKEY", equalTo(apiKey))
          .withQueryParam("dualSidePosition", equalTo(true.toString))
          .withQueryParam("recvWindow", equalTo("5000"))
          .withQueryParam("timestamp", equalTo(fixedTime.toString))
          .withQueryParam("signature", equalTo("4fb3c513fff4c269ad18ffcf45f44b9f46205f51696ba7e5ed91ac489f42ed75"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                        |{
                        |    "code": 200,
                        |    "msg": "success"
                        |}""".stripMargin)
          )
      )
    )

    IO.pure(new TestAsync(onRealtime = fixedTime.millis)).flatMap { implicit async =>
      for {
        _      <- stubInfoEndpoint(server)
        _      <- stubResponse
        config <- createConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)
        changePositionParams = true
        _ <- BinanceClient
          .createFutureClient[IO](config)
          .use(_.changePositionMode(changePositionParams))
      } yield expect(server.getAllServeEvents.asScala.count { event =>
        event.getRequest.getUrl.contains("/fapi/v1/positionSide/dual") &&
        event.getResponse.getStatus == 200
      } == 1)
    }
  }

  integrationTest("it should be able to change the initial leverage") { case WebServer(server, _) =>
    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    val stubResponse = IO.delay(
      server.stubFor(
        post(urlPathMatching("/fapi/v1/leverage"))
          .withHeader("X-MBX-APIKEY", equalTo(apiKey))
          .withQueryParam("symbol", equalTo("BTCUSDT"))
          .withQueryParam("leverage", equalTo(100.toString))
          .withQueryParam("recvWindow", equalTo("5000"))
          .withQueryParam("timestamp", equalTo(fixedTime.toString))
          .withQueryParam("signature", equalTo("0c8dbb8c3a8f2dc9a071a5860e4686c4ddff9dcdfcc83eb2aba57805b2c369b2"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                      |{
                      |    "leverage": 100,
                      |    "maxNotionalValue": "1000000",
                      |    "symbol": "BTCUSDT"
                      |}
                      """.stripMargin)
          )
      )
    )

    IO.pure(new TestAsync(onRealtime = fixedTime.millis)).flatMap { implicit async =>
      for {
        _      <- stubInfoEndpoint(server)
        _      <- stubResponse
        config <- createConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)
        changeLeverageParams = ChangeInitialLeverageParams(symbol = "BTCUSDT", leverage = 100)
        result <- BinanceClient
          .createFutureClient[IO](config)
          .use(_.changeInitialLeverage(changeLeverageParams))
      } yield expect(
        result == ChangeInitialLeverageResponse(
          symbol = "BTCUSDT",
          leverage = 100,
          maxNotionalValue = MaxNotionalValue.Value(1000000)
        )
      )
    }
  }

  integrationTest("it should create an order") { case WebServer(server, _) =>
    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    val stubResponse = IO.delay(
      server.stubFor(
        post(urlPathMatching("/fapi/v1/order"))
          .withHeader("X-MBX-APIKEY", equalTo(apiKey))
          .withQueryParams(
            Map(
              "recvWindow"   -> equalTo("5000"),
              "timestamp"    -> equalTo(fixedTime.toString),
              "type"         -> equalTo("MARKET"),
              "symbol"       -> equalTo("BTCUSDT"),
              "side"         -> equalTo("BUY"),
              "positionSide" -> equalTo("BOTH"),
              "quantity"     -> equalTo("10"),
              "signature"    -> equalTo("d34c07e97437033bb7e960bdd219b3293a1a511068782653b95304358cf85d94")
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(201)
              .withBody("""{
                        |    "clientOrderId": "testOrder",
                        |    "cumQty": "0",
                        |    "cumQuote": "0",
                        |    "executedQty": "0",
                        |    "orderId": 22542179,
                        |    "avgPrice": "0.00000",
                        |    "origQty": "10",
                        |    "price": "0",
                        |    "reduceOnly": false,
                        |    "side": "BUY",
                        |    "positionSide": "SHORT",
                        |    "status": "NEW",
                        |    "stopPrice": "9300",
                        |    "closePosition": false,
                        |    "symbol": "BTCUSDT",
                        |    "timeInForce": "GTC",
                        |    "type": "TRAILING_STOP_MARKET",
                        |    "origType": "TRAILING_STOP_MARKET",
                        |    "activatePrice": "9020",
                        |    "priceRate": "0.3",
                        |    "updateTime": 1566818724722,
                        |    "workingType": "CONTRACT_PRICE",
                        |    "priceProtect": false
                        |}""".stripMargin)
          )
      )
    )

    IO.pure(new TestAsync(onRealtime = fixedTime.millis)).flatMap { implicit async =>
      for {
        _      <- stubInfoEndpoint(server)
        _      <- stubResponse
        config <- createConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)
        result <- BinanceClient
          .createFutureClient[IO](config)
          .use(
            _.createOrder(
              FutureOrderCreateParams.MARKET(
                symbol = "BTCUSDT",
                side = OrderSide.BUY,
                positionSide = FuturePositionSide.BOTH,
                quantity = 10
              )
            )
          )
      } yield expect(result.isInstanceOf[FutureOrderCreateResponse])
    }
  }

  integrationTest("it should get an order details") { case WebServer(server, _) =>
    val orderId   = 22542179L
    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    val stubResponse = IO.delay(
      server.stubFor(
        get(urlPathMatching("/fapi/v1/order"))
          .withHeader("X-MBX-APIKEY", equalTo(apiKey))
          .withQueryParams(
            Map(
              "recvWindow" -> equalTo("5000"),
              "timestamp"  -> equalTo(fixedTime.toString),
              "orderId"    -> equalTo(orderId.toString),
              "signature"  -> equalTo("ad492d2b0950baa9ff3aa0908281ad83cc2132d835daa0cb87d673d83b275fbd")
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""{
                        |    "clientOrderId": "testOrder",
                        |    "cumQuote": "0",
                        |    "executedQty": "0",
                        |    "orderId": 22542179,
                        |    "avgPrice": "0.00000",
                        |    "origQty": "10",
                        |    "price": "0",
                        |    "reduceOnly": false,
                        |    "side": "BUY",
                        |    "positionSide": "SHORT",
                        |    "status": "NEW",
                        |    "stopPrice": "9300",
                        |    "closePosition": false,
                        |    "symbol": "BTCUSDT",
                        |    "time": "500",
                        |    "timeInForce": "GTC",
                        |    "type": "TRAILING_STOP_MARKET",
                        |    "origType": "TRAILING_STOP_MARKET",
                        |    "activatePrice": "9020",
                        |    "priceRate": "0.3",
                        |    "updateTime": 1566818724722,
                        |    "workingType": "CONTRACT_PRICE",
                        |    "priceProtect": false
                        |}""".stripMargin)
          )
      )
    )

    IO.pure(new TestAsync(onRealtime = fixedTime.millis)).flatMap { implicit async =>
      for {
        _      <- stubInfoEndpoint(server)
        _      <- stubResponse
        config <- createConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)
        result <- BinanceClient
          .createFutureClient[IO](config)
          .use(
            _.getOrder(
              FutureGetOrderParams.OrderId(
                symbol = "BTCUSDT",
                orderId = orderId
              )
            )
          )
      } yield expect(result.isInstanceOf[FutureOrderGetResponse])
    }
  }

  integrationTest("it should get all current open order details") { case WebServer(server, _) =>
    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    val stubResponse = IO.delay(
      server.stubFor(
        get(urlPathMatching("/fapi/v1/openOrders"))
          .withHeader("X-MBX-APIKEY", equalTo(apiKey))
          .withQueryParams(
            Map(
              "recvWindow" -> equalTo("5000"),
              "timestamp"  -> equalTo(fixedTime.toString),
              "signature"  -> equalTo("8a31f1e30c7c9ecd7c9b4b7e3ab6f45c8a04926af3aebed822798b9e550ac55d")
            ).asJava
          )
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""
                        |[
                        |  {
                        |    "avgPrice": "0.00000",
                        |    "clientOrderId": "abc",
                        |    "cumQuote": "0",
                        |    "executedQty": "0",
                        |    "orderId": 1917641,
                        |    "origQty": "0.40",
                        |    "origType": "TRAILING_STOP_MARKET",
                        |    "price": "0",
                        |    "reduceOnly": false,
                        |    "side": "BUY",
                        |    "positionSide": "SHORT",
                        |    "status": "NEW",
                        |    "stopPrice": "9300",
                        |    "closePosition": false,
                        |    "symbol": "BTCUSDT",
                        |    "time": 1579276756075,   
                        |    "timeInForce": "GTC",
                        |    "type": "TRAILING_STOP_MARKET",
                        |    "activatePrice": "9020",  
                        |    "priceRate": "0.3",
                        |    "updateTime": 1579276756075,   
                        |    "workingType": "CONTRACT_PRICE",
                        |    "priceProtect": false    
                        |  }
                        |]""".stripMargin)
          )
      )
    )

    IO.pure(new TestAsync(onRealtime = fixedTime.millis)).flatMap { implicit async =>
      for {
        _      <- stubInfoEndpoint(server)
        _      <- stubResponse
        config <- createConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)
        result <- BinanceClient
          .createFutureClient[IO](config)
          .use(_.getAllOpenOrders(symbol = "BTCUSDT"))
      } yield expect(result.isInstanceOf[List[FutureOrderGetResponse]])
    }
  }

  integrationTest("it should cancel an order") { case WebServer(server, _) =>
    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    val stubResponse = IO.delay(
      server.stubFor(
        delete(urlPathMatching("/fapi/v1/order"))
          .withHeader("X-MBX-APIKEY", equalTo(apiKey))
          .withQueryParam("recvWindow", equalTo("5000"))
          .withQueryParam("timestamp", equalTo(fixedTime.toString))
          .withQueryParam("signature", equalTo("5cb34cc9078d1474d997f91c68fc225c408bc6d8773a76abdbe00a19969d973c"))
          .willReturn(
            aResponse()
              .withStatus(201)
              .withBody("""
                        |{
                        |  "symbol": "LTCBTC",
                        |  "origClientOrderId": "myOrder1",
                        |  "orderId": 4,
                        |  "orderListId": -1,
                        |  "clientOrderId": "cancelMyOrder1",
                        |  "price": "2.00000000",
                        |  "origQty": "1.00000000",
                        |  "executedQty": "0.00000000",
                        |  "cummulativeQuoteQty": "0.00000000",
                        |  "status": "CANCELED",
                        |  "timeInForce": "GTC",
                        |  "type": "LIMIT",
                        |  "side": "BUY"
                        |}
                      """.stripMargin)
          )
      )
    )

    IO.pure(new TestAsync(onRealtime = fixedTime.millis)).flatMap { implicit async =>
      for {
        _      <- stubInfoEndpoint(server)
        _      <- stubResponse
        config <- createConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)
        _ <- BinanceClient
          .createFutureClient[IO](config)
          .use(
            _.cancelOrder(
              FutureOrderCancelParams(symbol = "BTCUSDT", orderId = 1L.some, origClientOrderId = None)
            )
          )
      } yield success
    }
  }

  integrationTest("it should cancel all orders") { case WebServer(server, _) =>
    val fixedTime = 1499827319559L

    val apiKey    = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
    val apiSecret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"

    val stubResponse = IO.delay(
      server.stubFor(
        delete(urlPathMatching("/fapi/v1/allOpenOrders"))
          .withHeader("X-MBX-APIKEY", equalTo(apiKey))
          .withQueryParam("recvWindow", equalTo("5000"))
          .withQueryParam("timestamp", equalTo(fixedTime.toString))
          .withQueryParam("signature", equalTo("8a31f1e30c7c9ecd7c9b4b7e3ab6f45c8a04926af3aebed822798b9e550ac55d"))
          .willReturn(
            aResponse()
              .withStatus(201)
              .withBody("""
                        |[
                        |  {
                        |    "symbol": "BTCUSDT",
                        |    "origClientOrderId": "E6APeyTJvkMvLMYMqu1KQ4",
                        |    "orderId": 11,
                        |    "orderListId": -1,
                        |    "clientOrderId": "pXLV6Hz6mprAcVYpVMTGgx",
                        |    "price": "0.089853",
                        |    "origQty": "0.178622",
                        |    "executedQty": "0.000000",
                        |    "cummulativeQuoteQty": "0.000000",
                        |    "status": "CANCELED",
                        |    "timeInForce": "GTC",
                        |    "type": "LIMIT",
                        |    "side": "BUY"
                        |  },
                        |  {
                        |    "symbol": "BTCUSDT",
                        |    "origClientOrderId": "A3EF2HCwxgZPFMrfwbgrhv",
                        |    "orderId": 13,
                        |    "orderListId": -1,
                        |    "clientOrderId": "pXLV6Hz6mprAcVYpVMTGgx",
                        |    "price": "0.090430",
                        |    "origQty": "0.178622",
                        |    "executedQty": "0.000000",
                        |    "cummulativeQuoteQty": "0.000000",
                        |    "status": "CANCELED",
                        |    "timeInForce": "GTC",
                        |    "type": "LIMIT",
                        |    "side": "BUY"
                        |  },
                        |  {
                        |    "orderListId": 1929,
                        |    "contingencyType": "OCO",
                        |    "listStatusType": "ALL_DONE",
                        |    "listOrderStatus": "ALL_DONE",
                        |    "listClientOrderId": "2inzWQdDvZLHbbAmAozX2N",
                        |    "transactionTime": 1585230948299,
                        |    "symbol": "BTCUSDT",
                        |    "orders": [
                        |      {
                        |        "symbol": "BTCUSDT",
                        |        "orderId": 20,
                        |        "clientOrderId": "CwOOIPHSmYywx6jZX77TdL"
                        |      },
                        |      {
                        |        "symbol": "BTCUSDT",
                        |        "orderId": 21,
                        |        "clientOrderId": "461cPg51vQjV3zIMOXNz39"
                        |      }
                        |    ],
                        |    "orderReports": [
                        |      {
                        |        "symbol": "BTCUSDT",
                        |        "origClientOrderId": "CwOOIPHSmYywx6jZX77TdL",
                        |        "orderId": 20,
                        |        "orderListId": 1929,
                        |        "clientOrderId": "pXLV6Hz6mprAcVYpVMTGgx",
                        |        "price": "0.668611",
                        |        "origQty": "0.690354",
                        |        "executedQty": "0.000000",
                        |        "cummulativeQuoteQty": "0.000000",
                        |        "status": "CANCELED",
                        |        "timeInForce": "GTC",
                        |        "type": "STOP_LOSS_LIMIT",
                        |        "side": "BUY",
                        |        "stopPrice": "0.378131",
                        |        "icebergQty": "0.017083"
                        |      },
                        |      {
                        |        "symbol": "BTCUSDT",
                        |        "origClientOrderId": "461cPg51vQjV3zIMOXNz39",
                        |        "orderId": 21,
                        |        "orderListId": 1929,
                        |        "clientOrderId": "pXLV6Hz6mprAcVYpVMTGgx",
                        |        "price": "0.008791",
                        |        "origQty": "0.690354",
                        |        "executedQty": "0.000000",
                        |        "cummulativeQuoteQty": "0.000000",
                        |        "status": "CANCELED",
                        |        "timeInForce": "GTC",
                        |        "type": "LIMIT_MAKER",
                        |        "side": "BUY",
                        |        "icebergQty": "0.639962"
                        |      }
                        |    ]
                        |  }
                        |]
                        """.stripMargin)
          )
      )
    )

    IO.pure(new TestAsync(onRealtime = fixedTime.millis)).flatMap { implicit async =>
      for {
        _      <- stubInfoEndpoint(server)
        _      <- stubResponse
        config <- createConfiguration(server, apiKey = apiKey, apiSecret = apiSecret)
        _ <- BinanceClient
          .createFutureClient[IO](config)
          .use(
            _.cancelAllOrders(
              FutureOrderCancelAllParams(symbol = "BTCUSDT")
            )
          )
      } yield success
    }
  }

  integrationTest("it should stream aggregate trade information") { case WebServer(server, ws) =>
    val toClient: Stream[IO, WebSocketFrame] = Stream(
      WebSocketFrame.Text("""{
                              |  "e": "aggTrade",
                              |  "E": 1623095242152,
                              |  "a": 102141499,
                              |  "s": "BTCUSDT",
                              |  "p": "39792.73",
                              |  "q": "10.543",
                              |  "f": 183139249,
                              |  "l": 183139250,
                              |  "T": 1623095241998,
                              |  "m": true
                              |}""".stripMargin),
      WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
    )

    stubInfoEndpoint(server) *> (for {
      config <- createConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = ws.port)
      result <- testStream(ws, config)(toClient)(_.aggregateTradeStreams("btcusdt").compile.toList)
    } yield expect(
      result == List(
        AggregateTradeStream(
          e = "aggTrade",
          E = 1623095242152L,
          s = "BTCUSDT",
          a = 102141499,
          p = 39792.73,
          q = 10.543,
          f = 183139249,
          l = 183139250,
          T = 1623095241998L,
          m = true
        )
      )
    ))
  }

  integrationTest("it should stream KLines") { case WebServer(server, ws) =>
    val toClient: Stream[IO, WebSocketFrame] = Stream(
      WebSocketFrame.Text("""{
                              |  "e": "kline",
                              |  "E": 123456789,
                              |  "s": "BTCUSDT",
                              |  "k": {
                              |    "t": 123400000,
                              |    "T": 123460000,
                              |    "s": "BTCUSDT",
                              |    "i": "1m",
                              |    "f": 100,
                              |    "L": 200,
                              |    "o": "0.0010",
                              |    "c": "0.0020",
                              |    "h": "0.0025",
                              |    "l": "0.0015",
                              |    "v": "1000",
                              |    "n": 100,
                              |    "x": false,
                              |    "q": "1.0000",
                              |    "V": "500",
                              |    "Q": "0.500",
                              |    "B": "123456"
                              |  }
                              |}""".stripMargin),
      WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
    )

    stubInfoEndpoint(server) *>
      (
        for {
          config <- createConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = ws.port)
          result <- testStream(ws, config)(toClient)(_.kLineStreams("btcusdt", Interval.`1m`).compile.toList)
        } yield expect(
          result == List(
            KLineStream(
              e = "kline",
              E = 123456789L,
              s = "BTCUSDT",
              k = KLineStreamPayload(
                t = 123400000,
                T = 123460000,
                s = "BTCUSDT",
                i = Interval.`1m`,
                f = 100,
                L = 200,
                o = 0.0010,
                c = 0.0020,
                h = 0.0025,
                l = 0.0015,
                v = 1000,
                n = 100,
                x = false,
                q = 1.0000,
                V = 500,
                Q = 0.500
              )
            )
          )
        )
      )
  }

  integrationTest("it should stream continuous Contract KLines") { case WebServer(server, ws) =>
    val toClient: Stream[IO, WebSocketFrame] = Stream(
      WebSocketFrame.Text("""{
                              |  "e":"continuous_kline",
                              |  "E":1607443058651,
                              |  "ps":"BTCUSDT",
                              |  "ct":"PERPETUAL",
                              |  "k":{
                              |    "t":1607443020000,
                              |    "T":1607443079999,
                              |    "i":"1m",
                              |    "f":116467658886,
                              |    "L":116468012423,
                              |    "o":"18787.00",
                              |    "c":"18804.04",
                              |    "h":"18804.04",
                              |    "l":"18786.54",
                              |    "v":"197.664",
                              |    "n": 543,
                              |    "x":false,
                              |    "q":"3715253.19494",
                              |    "V":"184.769",
                              |    "Q":"3472925.84746",
                              |    "B":"0"
                              |  }
                              |}""".stripMargin),
      WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
    )

    stubInfoEndpoint(server) *>
      (for {
        config <- createConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = ws.port)
        result <- testStream(ws, config)(toClient)(
          _.contractKLineStreams("btcusdt", FutureContractType.PERPETUAL, Interval.`1m`).compile.toList
        )
      } yield expect(
        result == List(
          ContractKLineStream(
            e = "continuous_kline",
            E = 1607443058651L,
            ps = "BTCUSDT",
            ct = FutureContractType.PERPETUAL,
            k = ContractKLineStreamPayload(
              t = 1607443020000L,
              T = 1607443079999L,
              i = Interval.`1m`,
              f = 116467658886L,
              L = 116468012423L,
              o = 18787.00,
              c = 18804.04,
              h = 18804.04,
              l = 18786.54,
              v = 197.664,
              n = 543,
              x = false,
              q = 3715253.19494,
              V = 184.769,
              Q = 3472925.84746
            )
          )
        )
      ))
  }

  integrationTest("it should stream Mark Price updates for a given symbol") { case WebServer(server, ws) =>
    val toClient: Stream[IO, WebSocketFrame] = Stream(
      WebSocketFrame.Text("""{
                              |  "e": "markPriceUpdate",
                              |  "E": 1562305380000,
                              |  "s": "BTCUSDT",
                              |  "p": "11794.15000000",
                              |  "i": "11784.62659091",
                              |  "P": "11784.25641265",
                              |  "r": "0.00038167",
                              |  "T": 1562306400000
                              |}""".stripMargin),
      WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
    )

    stubInfoEndpoint(server) *>
      (for {
        config <- createConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = ws.port)
        result <- testStream(ws, config)(toClient)(_.markPriceStream("btcusdt").compile.toList)
      } yield expect(
        result == List(
          MarkPriceUpdate(
            e = "markPriceUpdate",
            E = 1562305380000L,
            s = "BTCUSDT",
            p = 11794.15000000,
            i = 11784.62659091,
            P = 11784.25641265,
            r = 0.00038167,
            T = 1562306400000L
          )
        )
      ))
  }

  integrationTest("it should stream Mark Price for all symbols") { case WebServer(server, ws) =>
    val toClient: Stream[IO, WebSocketFrame] = Stream(
      WebSocketFrame.Text("""[{
                              |  "e": "markPriceUpdate",
                              |  "E": 1562305380000,
                              |  "s": "BTCUSDT",
                              |  "p": "11185.87786614",
                              |  "i": "11784.62659091",
                              |  "P": "11784.25641265",
                              |  "r": "0.00030000",
                              |  "T": 1562306400000
                              |}]""".stripMargin),
      WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
    )

    stubInfoEndpoint(server) *>
      (for {
        config <- createConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = ws.port)
        result <- testStream(ws, config)(toClient)(_.markPriceStream().compile.toList)
      } yield expect(
        result == List(
          MarkPriceUpdate(
            e = "markPriceUpdate",
            E = 1562305380000L,
            s = "BTCUSDT",
            p = 11185.87786614,
            i = 11784.62659091,
            P = 11784.25641265,
            r = 0.00030000,
            T = 1562306400000L
          )
        )
      ))
  }

  integrationTest("it should stream Partial Book Depth streams") { case WebServer(server, ws) =>
    val toClient: Stream[IO, WebSocketFrame] = Stream(
      WebSocketFrame.Text("""{
                              |  "e": "depthUpdate",
                              |  "E": 1571889248277, 
                              |  "T": 1571889248276, 
                              |  "s": "BTCUSDT",
                              |  "U": 390497796,
                              |  "u": 390497878,
                              |  "pu": 390497794,
                              |  "b": [          
                              |    [ "7403.89", "0.002" ],
                              |    [ "7403.90", "3.906" ],
                              |    [ "7404.00", "1.428" ],
                              |    [ "7404.85", "5.239" ],
                              |    [ "7405.43", "2.562" ]
                              |  ],
                              |  "a": [          
                              |    [ "7405.96", "3.340" ],
                              |    [ "7406.63", "4.525" ],
                              |    [ "7407.08", "2.475" ],
                              |    [ "7407.15", "4.800" ],
                              |    [ "7407.20", "0.175" ]
                              |  ] 
                              |}""".stripMargin),
      WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
    )

    for {
      _      <- stubInfoEndpoint(server)
      config <- createConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = ws.port)
      result <- testStream(ws, config)(toClient)(
        _.partialBookDepthStream("btcusdt", Level.`5`, DepthUpdateSpeed.`100ms`).compile.toList
      )
    } yield expect(
      result == List(
        PartialDepthStream(
          e = "depthUpdate",
          E = 1571889248277L,
          T = 1571889248276L,
          s = "BTCUSDT",
          U = 390497796,
          u = 390497878,
          pu = 390497794,
          b = Seq(
            Bid(7403.89, 0.002),
            Bid(7403.90, 3.906),
            Bid(7404.00, 1.428),
            Bid(7404.85, 5.239),
            Bid(7405.43, 2.562)
          ),
          a = Seq(
            Ask(7405.96, 3.340),
            Ask(7406.63, 4.525),
            Ask(7407.08, 2.475),
            Ask(7407.15, 4.800),
            Ask(7407.20, 0.175)
          )
        )
      )
    )
  }

  integrationTest("it should stream Diff. Depth") { case WebServer(server, ws) =>
    val toClient: Stream[IO, WebSocketFrame] = Stream(
      WebSocketFrame.Text("""{
                              |  "e": "depthUpdate", 
                              |  "E": 123456789,
                              |  "T": 123456788,
                              |  "s": "BTCUSDT",
                              |  "U": 157,      
                              |  "u": 160,      
                              |  "pu": 149,     
                              |  "b": [         
                              |    [
                              |      "0.0024",       
                              |      "10"       
                              |    ]
                              |  ],
                              |  "a": [         
                              |    [
                              |      "0.0026",       
                              |      "100"     
                              |    ]
                              |  ]
                              |}""".stripMargin),
      WebSocketFrame.Binary(ByteVector.empty) // force the stream to complete
    )

    for {
      _      <- stubInfoEndpoint(server)
      config <- createConfiguration(server, apiKey = "apiKey", apiSecret = "apiSecret", wsPort = ws.port)
      result <- testStream(ws, config)(toClient)(_.diffDepthStream("btcusdt", DepthUpdateSpeed.`100ms`).compile.toList)
    } yield expect(
      result == List(
        DiffDepthStream(
          e = "depthUpdate",
          E = 123456789,
          T = 123456788,
          s = "BTCUSDT",
          U = 157,
          u = 160,
          pu = 149,
          b = Seq(Bid(0.0024, 10)),
          a = Seq(Ask(0.0026, 100))
        )
      )
    )
  }

  private def stubInfoEndpoint(server: WireMockServer) = IO.delay {
    server.stubFor(
      get("/fapi/v1/exchangeInfo")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""
                        |{
                        |  "timezone":"UTC",
                        |  "serverTime":1621543436177,
                        |  "futuresType":"U_MARGINED",
                        |  "rateLimits": [
                        |    {
                        |      "rateLimitType": "REQUEST_WEIGHT",
                        |      "interval": "MINUTE",
                        |      "intervalNum": 1,
                        |      "limit": 1200
                        |    }
                        |  ],
                        |  "exchangeFilters":[],
                        |  "assets": [],
                        |  "symbols":[]
                        |}
                      """.stripMargin)
        )
    )
  }

  private def createConfiguration(
      server: WireMockServer,
      apiKey: String = "",
      apiSecret: String = "",
      wsPort: Int = 80
  ) =
    FapiConfig
      .Custom[IO](
        restBaseUrl = uri"http://localhost:${server.port}",
        wsBaseUrl = uri"ws://localhost:$wsPort",
        exchangeInfoUrl = uri"http://localhost:${server.port}/fapi/v1/exchangeInfo",
        apiKey = apiKey,
        apiSecret = apiSecret
      )
      .pure[IO]

  private def testStream[T](ws: TestWsServer[IO], config: FapiConfig)(
      toClient: Stream[IO, WebSocketFrame]
  )(f: FutureApi[IO] => IO[T]) = (for {
    client <- BinanceClient.createFutureClient[IO](config)
    _      <- ws.create(toClient)
  } yield client).use(f)
}
