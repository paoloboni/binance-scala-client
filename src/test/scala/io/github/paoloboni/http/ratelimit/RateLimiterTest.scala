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
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testkit.TestControl
import io.github.paoloboni.Env.log
import io.github.paoloboni.binance.common.response.RateLimitType
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class RateLimiterTest extends AsyncFreeSpec with AsyncIOSpec with Matchers with TypeCheckedTripleEquals {

  "it should rate limit when frequency is greater than limit" in {
    val perSecond = 10 // period = 100.millis

    TestControl
      .executeEmbed(for {
        rateLimiter <- RateLimiter.make[IO](perSecond, 1, RateLimitType.NONE)
        startTime   <- IO.monotonic
        _           <- rateLimiter.await(IO.pure(1))
        _           <- rateLimiter.await(IO.pure(2))
        result      <- rateLimiter.await(IO.pure(3))
        endTime     <- IO.monotonic
      } yield (result, startTime, endTime))
      .asserting { case (res, start, end) =>
        (end - start) should ===(300.millis)
        res should ===(3)
      }
  }

  "it should rate limit when frequency is greater than limit and weight > 1" in {
    val perSecond = 10 // period = 100.millis

    TestControl
      .executeEmbed(for {
        rateLimiter <- RateLimiter.make[IO](perSecond, 1, RateLimitType.NONE)
        startTime   <- IO.monotonic
        result      <- rateLimiter.await(IO.pure(1), weight = 10)
        endTime     <- IO.monotonic
      } yield (result, startTime, endTime))
      .asserting { case (res, start, end) =>
        (end - start) should ===(1.second)
        res should ===(1)
      }
  }

  "it should not rate limit when frequency is lower than limit" in {
    val perSecond = 1 // period = 1.second

    TestControl
      .executeEmbed(for {
        rateLimiter <- RateLimiter.make[IO](perSecond, 1, RateLimitType.NONE)
        startTime   <- IO.monotonic
        result      <- rateLimiter.await(IO.pure(1))
        endTime     <- IO.monotonic
      } yield (result, startTime, endTime))
      .asserting { case (res, start, end) =>
        (end - start) should ===(1.second)
        res should ===(1)
      }
  }
}
