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

package io.github.paoloboni.http.ratelimit

import cats.effect.IO
import cats.effect.testkit.TestControl
import io.github.paoloboni.Env.log
import io.github.paoloboni.binance.common.response.RateLimitType
import weaver.SimpleIOSuite

import scala.concurrent.duration.DurationInt

object RateLimiterTest extends SimpleIOSuite {

  test("it should rate limit when frequency is greater than limit") {
    val perSecond = 10 // period = 100.millis

    TestControl
      .executeEmbed(
        RateLimiter
          .make[IO](perSecond.toDouble, 1, RateLimitType.NONE)
          .use(rateLimiter =>
            (for {
              startTime <- IO.monotonic
              _         <- rateLimiter.await(IO.pure(1))
              _         <- rateLimiter.await(IO.pure(2))
              result    <- rateLimiter.await(IO.pure(3))
              endTime   <- IO.monotonic
            } yield (result, startTime, endTime))
          )
      )
      .map { case (res, start, end) =>
        expect.all(
          (end - start) == 300.millis,
          res == 3
        )
      }
  }

  test("it should rate limit when frequency is greater than limit and weight > 1") {
    val perSecond = 10 // period = 100.millis

    TestControl
      .executeEmbed(
        RateLimiter
          .make[IO](perSecond.toDouble, 1, RateLimitType.NONE)
          .use(rateLimiter =>
            (for {
              startTime <- IO.monotonic
              result    <- rateLimiter.await(IO.pure(1), weight = 10)
              endTime   <- IO.monotonic
            } yield (result, startTime, endTime))
          )
      )
      .map { case (res, start, end) =>
        expect.all(
          (end - start) == 1.second,
          res == 1
        )
      }
  }

  test("it should not rate limit when frequency is lower than limit") {
    val perSecond = 1 // period = 1.second

    TestControl
      .executeEmbed(
        RateLimiter
          .make[IO](perSecond.toDouble, 1, RateLimitType.NONE)
          .use(rateLimiter =>
            for {
              startTime <- IO.monotonic
              result    <- rateLimiter.await(IO.pure(1))
              endTime   <- IO.monotonic
            } yield (result, startTime, endTime)
          )
      )
      .map { case (res, start, end) =>
        expect.all(
          (end - start) == 1.seconds,
          res == 1
        )
      }
  }

  test("it should allow error to be bubble up back to the caller") {
    val perSecond     = 1 // period = 1.second
    val testThrowable = new Throwable("Request Error")

    TestControl
      .executeEmbed(
        RateLimiter
          .make[IO](perSecond.toDouble, 1, RateLimitType.NONE)
          .use(rateLimiter =>
            for {
              startTime <- IO.monotonic
              result <- rateLimiter
                .await(IO.raiseError[Throwable](testThrowable))
                .handleErrorWith(IO.pure)
              endTime <- IO.monotonic
            } yield (result, startTime, endTime)
          )
      )
      .map { case (res, start, end) =>
        expect.all(
          (end - start) == 1.seconds,
          res == testThrowable
        )
      }
  }
}
