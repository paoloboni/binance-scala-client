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

import cats.effect.{IO, Resource}
import com.github.tomakehurst.wiremock.WireMockServer
import io.github.paoloboni.binance.IntegrationTest.port
import weaver.{Expectations, SimpleIOSuite, TestName}

import java.util.concurrent.atomic.AtomicInteger

abstract class IntegrationTest extends SimpleIOSuite {

  case class WebServer(http: WireMockServer, ws: TestWsServer[IO])

  val resource: Resource[IO, WebServer] = Resource
    .make(
      IO.delay {
        val p      = port.decrementAndGet()
        val server = new WireMockServer(p)
        server.start()
        server
      }
    )(server => IO.delay(server.stop()))
    .flatMap { http =>
      testWsServer(http).map(ws => WebServer(http, ws))
    }

  def integrationTest(name: TestName)(expectations: WebServer => IO[Expectations]) =
    test(name)(resource.use(expectations))

  private def testWsServer(server: WireMockServer): Resource[IO, TestWsServer[IO]] =
    TestWsServer[IO](port = server.port() - 1000)
}

object IntegrationTest {
  val port = new AtomicInteger(9000)
}
