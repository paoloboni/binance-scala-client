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

package io.github.paoloboni.binance.common

import scala.concurrent.duration.{Duration, DurationInt}

import io.circe.Decoder
import io.github.paoloboni.binance.common.EnumDecoder

enum Interval(val duration: Duration) derives EnumDecoder:
  case `1m`  extends Interval(1.minute)
  case `3m`  extends Interval(3.minutes)
  case `5m`  extends Interval(5.minutes)
  case `15m` extends Interval(15.minutes)
  case `30m` extends Interval(30.minutes)
  case `1h`  extends Interval(1.hour)
  case `2h`  extends Interval(2.hours)
  case `4h`  extends Interval(4.hours)
  case `6h`  extends Interval(6.hours)
  case `8h`  extends Interval(8.hours)
  case `12h` extends Interval(12.hours)
  case `1d`  extends Interval(1.day)
  case `3d`  extends Interval(3.days)
  case `1w`  extends Interval(7.days)
