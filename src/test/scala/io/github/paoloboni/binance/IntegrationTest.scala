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

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import com.github.tomakehurst.wiremock.WireMockServer
import fs2.Stream
import io.github.paoloboni.binance.IntegrationTest.port
import org.http4s.websocket.WebSocketFrame
import org.scalactic.{TypeCheckedTripleEquals, source}
import org.scalatest.compatible
import org.scalatest.freespec.FixtureAsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

abstract class IntegrationTest
    extends FixtureAsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with TypeCheckedTripleEquals
    with CatsResourceIO[WireMockServer] {

  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override protected val ResourceTimeout: Duration = 10.seconds

  val resource: Resource[IO, WireMockServer] = Resource.make(
    IO.delay {
      val p      = port.decrementAndGet()
      val server = new WireMockServer(p)
      server.start()
      server
    }
  )(server => IO.delay(server.stop()))

  class EnhancedFreeSpecStringWrapper(string: String, pos: source.Position) {
    val delegate = new FreeSpecStringWrapper(string, pos)

    def in(testFun: FixtureParam => Future[compatible.Assertion]): Unit = {
      delegate.in(server => IO.delay(server.resetAll()).unsafeToFuture().flatMap(_ => testFun(server)))
    }
  }

  protected implicit def convertToEnhancedFreeSpecStringWrapper(s: String)(implicit
      pos: source.Position
  ): EnhancedFreeSpecStringWrapper = new EnhancedFreeSpecStringWrapper(s, pos)

  def testWsServer(server: WireMockServer, toClient: Stream[IO, WebSocketFrame]): IO[TestWsServer[IO]] =
    IO.delay(new TestWsServer[IO](toClient)(port = server.port() - 1000))
}

object IntegrationTest {
  val port = new AtomicInteger(9000)
}
