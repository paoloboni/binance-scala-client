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

package io.github.paoloboni

import cats.StackSafeMonad
import cats.effect.kernel.Sync
import cats.effect._
import cats.syntax.all._

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class TestAsync(
    onRealtime: FiniteDuration = FiniteDuration(System.currentTimeMillis(), TimeUnit.MILLISECONDS),
    onMonotonic: FiniteDuration = FiniteDuration(System.nanoTime(), TimeUnit.NANOSECONDS),
    onSleep: FiniteDuration => IO[Unit] = time => IO.sleep(time)
) extends kernel.Async[IO]
    with StackSafeMonad[IO] {

  override def async_[A](k: (Either[Throwable, A] => Unit) => Unit): IO[A] = {
    val body = new Cont[IO, A, A] {
      def apply[G[_]](implicit G: MonadCancel[G, Throwable]) = { (resume, get, lift) =>
        G.uncancelable { poll => lift(IO.delay(k(resume))).flatMap(_ => poll(get)) }
      }
    }

    cont(body)
  }

  override def as[A, B](ioa: IO[A], b: B): IO[B] =
    ioa.as(b)

  override def attempt[A](ioa: IO[A]) =
    ioa.attempt

  def forceR[A, B](left: IO[A])(right: IO[B]): IO[B] =
    left.attempt.productR(right)

  def pure[A](x: A): IO[A] =
    IO.pure(x)

  override def guarantee[A](fa: IO[A], fin: IO[Unit]): IO[A] =
    fa.guarantee(fin)

  override def guaranteeCase[A](fa: IO[A])(fin: OutcomeIO[A] => IO[Unit]): IO[A] =
    fa.guaranteeCase(fin)

  def handleErrorWith[A](fa: IO[A])(f: Throwable => IO[A]): IO[A] =
    fa.handleErrorWith(f)

  def raiseError[A](e: Throwable): IO[A] =
    IO.raiseError(e)

  def cont[K, R](body: Cont[IO, K, R]): IO[R] = IO.cont(body)

  def evalOn[A](fa: IO[A], ec: ExecutionContext): IO[A] =
    fa.evalOn(ec)

  val executionContext: IO[ExecutionContext] =
    IO.executionContext

  def onCancel[A](ioa: IO[A], fin: IO[Unit]): IO[A] =
    ioa.onCancel(fin)

  override def bracketFull[A, B](acquire: Poll[IO] => IO[A])(use: A => IO[B])(
      release: (A, OutcomeIO[B]) => IO[Unit]
  ): IO[B] =
    IO.bracketFull(acquire)(use)(release)

  val monotonic: IO[FiniteDuration] = onMonotonic.pure[IO]

  val realTime: IO[FiniteDuration] = onRealtime.pure[IO]

  def sleep(time: FiniteDuration): IO[Unit] = onSleep(time)

  override def timeoutTo[A](ioa: IO[A], duration: FiniteDuration, fallback: IO[A]): IO[A] =
    ioa.timeoutTo(duration, fallback)

  def canceled: IO[Unit] =
    IO.canceled

  def cede: IO[Unit] = IO.cede

  override def productL[A, B](left: IO[A])(right: IO[B]): IO[A] =
    left.productL(right)

  override def productR[A, B](left: IO[A])(right: IO[B]): IO[B] =
    left.productR(right)

  def start[A](fa: IO[A]): IO[FiberIO[A]] =
    fa.start

  def uncancelable[A](body: Poll[IO] => IO[A]): IO[A] =
    IO.uncancelable(body)

  override def map[A, B](fa: IO[A])(f: A => B): IO[B] =
    fa.map(f)

  def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] =
    fa.flatMap(f)

  override def delay[A](thunk: => A): IO[A] = IO(thunk)

  override def blocking[A](thunk: => A): IO[A] = IO.blocking(thunk)

  override def interruptible[A](thunk: => A) = IO.interruptible(thunk)

  def suspend[A](hint: Sync.Type)(thunk: => A): IO[A] =
    IO.suspend(hint)(thunk)

  override def void[A](ioa: IO[A]): IO[Unit] = ioa.void

  override def redeem[A, B](fa: IO[A])(recover: Throwable => B, f: A => B): IO[B] =
    fa.redeem(recover, f)

  override def redeemWith[A, B](fa: IO[A])(recover: Throwable => IO[B], bind: A => IO[B]): IO[B] =
    fa.redeemWith(recover, bind)

  override def ref[A](a: A): IO[Ref[IO, A]] = IO.ref(a)

  override def deferred[A]: IO[Deferred[IO, A]] = IO.deferred
}
