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

import cats.effect.kernel.{GenConcurrent, Temporal}
import cats.effect.std.Queue
import cats.Applicative
import cats.effect.syntax.spawn._
import cats.effect.{Spawn, Sync}
import cats.implicits._
import fs2.Stream
import io.github.paoloboni.binance.common.response.RateLimitType

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object RateLimiter {
  def make[F[_]: Temporal](
      perSecond: Double,
      bufferSize: Int,
      `type`: RateLimitType
  ): F[RateLimiter[F]] = {
    require(perSecond > 0 && bufferSize > 0)
    val period: FiniteDuration = periodFrom(perSecond)
    for {
      queue <- Queue.bounded[F, Unit](bufferSize)
      _     <- Stream.awakeDelay(period).evalMap(_ => queue.take).repeat.compile.drain.start.void
    } yield new RateLimiter[F] {
      override def rateLimit[T](effect: => F[T]): F[T] =
        queue.offer(()) *> effect
      override val limitType: RateLimitType = `type`
    }
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

case class Rate(n: Int, t: FiniteDuration, limitType: RateLimitType) {
  def perSecond: Double = n.toDouble / t.toSeconds.toDouble
}
