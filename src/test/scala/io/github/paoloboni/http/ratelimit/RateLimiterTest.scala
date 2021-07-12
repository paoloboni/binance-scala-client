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

package io.github.paoloboni.http.ratelimit

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{Async, IO}
import io.github.paoloboni.binance.common.response.RateLimitType
import io.github.paoloboni.{Env, TestAsync}
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

// very hacky test - didn't find a good way of controlling the scheduler in tests
class RateLimiterTest extends AsyncFreeSpec with AsyncIOSpec with Matchers with Env with TypeCheckedTripleEquals {

  "it should rate limit when frequency is greater than limit" in {
    val perSecond = 10
    val period    = 100.millis

    val initialTime = FiniteDuration(System.nanoTime(), TimeUnit.NANOSECONDS)
    var time        = initialTime

    implicit val async: Async[IO] = new TestAsync(
      onMonotonic = time,
      onSleep = sleepFor =>
        IO.delay {
          time = time + sleepFor
        }
    )

    (for {
      rateLimiter <- RateLimiter.make[IO](perSecond, 1, RateLimitType.NONE)
      _           <- rateLimiter.await(IO.pure(1))
      _           <- rateLimiter.await(IO.pure(2))
      result      <- rateLimiter.await(IO.pure(3))
    } yield result).timeout(5.seconds).asserting { res =>
      res should ===(3)
      val periodMillis = period.toMillis
      (time - initialTime).toMillis should ===(3 * periodMillis + periodMillis)
    }
  }

  "it should rate limit when frequency is greater than limit and weight > 1" in {
    val perSecond = 10
    val period    = 100.millis

    val initialTime = FiniteDuration(System.nanoTime(), TimeUnit.NANOSECONDS)
    var time        = initialTime

    implicit val async: Async[IO] = new TestAsync(
      onMonotonic = time,
      onSleep = sleepFor =>
        IO.delay {
          time = time + sleepFor
        }
    )

    (for {
      rateLimiter <- RateLimiter.make[IO](perSecond, 1, RateLimitType.NONE)
      result      <- rateLimiter.await(IO.pure(1), weight = 10)
    } yield result).timeout(5.seconds).asserting { res =>
      res should ===(1)
      val periodMillis = period.toMillis
      (time - initialTime).toMillis should ===(10 * periodMillis + periodMillis)
    }
  }

  "it should not rate limit when frequency is lower than limit" in {
    val perSecond = 1
    val period    = 1.second

    val initialTime = FiniteDuration(System.nanoTime(), TimeUnit.NANOSECONDS)
    var time        = initialTime

    implicit val async: Async[IO] = new TestAsync(
      onMonotonic = time,
      onSleep = sleepFor =>
        IO.delay {
          time = time + sleepFor
        }
    )

    (for {
      rateLimiter <- RateLimiter.make[IO](perSecond, 1, RateLimitType.NONE)
      result      <- rateLimiter.await(IO.pure(1))
    } yield result).timeout(5.seconds).asserting { res =>
      res should ===(1)
      (time - initialTime).toMillis should ===((period + period).toMillis)
    }
  }
}
