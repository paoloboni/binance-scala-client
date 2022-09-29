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

package io.github.paoloboni.binance.common.response

import io.github.paoloboni.binance.common.Interval

case class KLineStreamPayload(
    t: Long,       // Kline start time
    T: Long,       // Kline close time
    s: String,     // Symbol
    i: Interval,   // Interval. The time period until a kline will close and next opens.
    f: Long,       // First trade ID
    L: Long,       // Last trade ID
    o: BigDecimal, // Open price. Resets each time a kline is opened but otherwise remains stable
    c: BigDecimal, // Close price. When kline opened is same as open price, then updates progressively each stream update until close.
    h: BigDecimal, // High price. Updates progressively each stream update until close
    l: BigDecimal, // Low price. Updates progressively each stream update until close
    v: BigDecimal, // Base asset volume
    n: Int,        // Number of trades
    x: Boolean, // Is this kline closed on this stream update? When closed, volume and trade counter reset to zero and a new open price is set
    q: BigDecimal, // Quote asset volume
    V: BigDecimal, // Taker buy base asset volume
    Q: BigDecimal  // Taker buy quote asset volume
)
case class KLineStream(
    e: String, // Event type
    E: Long,   // Event time
    s: String, // Symbol
    k: KLineStreamPayload
) {
    def eventType: String = e
    def eventTime: Long = E
    def symbol: String = s
}
