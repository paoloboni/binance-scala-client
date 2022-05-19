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

import cats.Applicative
import cats.effect.kernel.{Async, Deferred, Resource}
import cats.effect.std.Queue
import cats.syntax.all._
import cats.effect.syntax.all._

import fs2.Stream
import io.github.paoloboni.binance.common
import io.github.paoloboni.binance.common.response.RateLimitType

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object RateLimiter {
  def make[F[_]](
      perSecond: Double,
      bufferSize: Int,
      `type`: RateLimitType
  )(implicit F: Async[F]): Resource[F, RateLimiter[F]] = {
    Resource.eval(
      F.unlessA(perSecond > 0 && bufferSize > 0)(
        F.raiseError(new IllegalArgumentException("perSecond and buffer size must be strictly positive"))
      )
    ) *>
      (for {
        queue <- Queue.bounded[F, F[Unit]](bufferSize).toResource
        period = periodFrom(perSecond)
        _ <- Stream.awakeDelay[F](period).evalMap(_ => queue.take.flatten).repeat.compile.drain.background
      } yield {
        new RateLimiter[F] {
          override def rateLimit[T](effect: => F[T]): F[T] = Deferred[F, T].flatMap { p =>
            queue.offer(F.defer(effect.flatTap(p.complete).void)) *> p.get
          }
          override val limitType: RateLimitType = `type`
        }
      })
  }
  private def periodFrom(perSecond: Double) =
    (1.second.toNanos.toDouble / perSecond).toInt.nanos

  def noOp[F[+_]: Applicative]: F[RateLimiter[F]] =
    new RateLimiter[F] {
      override def rateLimit[T](effect: => F[T]): F[T] = effect
      override val limitType: RateLimitType            = RateLimitType.NONE
    }.pure[F]
}

sealed trait RateLimiter[F[_]] {
  def rateLimit[T](effect: => F[T]): F[T]
  def limitType: RateLimitType
}

case class RateLimiters[F[_]](value: List[RateLimiter[F]]) {
  lazy val requestsOnly: List[RateLimiter[F]] = value.filterNot(_.limitType == common.response.RateLimitType.ORDERS)
}

case class Rate(n: Int, t: FiniteDuration, limitType: RateLimitType) {
  lazy val perSecond: Double = n.toDouble / t.toSeconds.toDouble
}
