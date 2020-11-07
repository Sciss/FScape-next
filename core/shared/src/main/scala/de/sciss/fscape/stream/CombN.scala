/*
 *  CombN.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape4, Inlet}
import de.sciss.fscape.stream.impl.Handlers._
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.Log.{stream => logStream}

import scala.math.{max, min, exp, abs, signum}

// XXX TODO: DRY with DelayN
object CombN {
  def apply(in: OutD, maxLength: OutI, length: OutI, decay: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in        , stage.in0)
    b.connect(maxLength , stage.in1)
    b.connect(length    , stage.in2)
    b.connect(decay     , stage.in3)
    stage.out
  }

  private final val name = "CombN"

  private type Shp = FanInShape4[BufD, BufI, BufI, BufD, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = new FanInShape4(
      in0 = InD (s"$name.in"        ),
      in1 = InI (s"$name.maxLength" ),
      in2 = InI (s"$name.length"    ),
      in3 = InD (s"$name.length"    ),
      out = OutD(s"$name.out"       )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer)
  }

  private final val Log0_001 = math.log(0.001)

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers[Shp](name, layer, shape) {

    type A = Double
    private val tpe = StreamType.double

    private[this] val hIn       : InDMain   = InDMain (this, shape.in0)
    private[this] val hMaxDlyLen: InIAux    = InIAux  (this, shape.in1)(max(0, _))
    private[this] val hDlyLen   : InIAux    = InIAux  (this, shape.in2)(max(0, _))
    private[this] val hDecayLen : InDAux    = InDAux  (this, shape.in3)()
    private[this] val hOut      : OutDMain  = OutDMain(this, shape.out)
    private[this] var needsLen    = true
    private[this] var buf: Array[A] = _   // circular
    private[this] var maxDlyLen   = 0
    private[this] var bufLen      = 0
    private[this] var bufPosIn    = 0   // the base position before adding delay
    private[this] var bufPosOut   = 0
    private[this] var advance     = 0   // in-pointer ahead of out-pointer
    private[this] var dlyLen      = -1
    private[this] var dcyLen      = Double.PositiveInfinity
    private[this] var fbCoef      = 1.0

    protected def onDone(inlet: Inlet[_]): Unit = {
      assert (inlet == shape.in0)
      if (!checkInDone()) process() // the rules change (`maxOut`)
    }

    private def checkInDone(): Boolean = {
      val res = advance == 0 && hOut.flush()
      if (res) completeStage()
      res
    }

    override protected def stopped(): Unit = {
      super.stopped()
      buf = null
    }

    protected def process(): Unit = {
      logStream.debug(s"process() $this")

      if (needsLen) {
        if (!hMaxDlyLen.hasNext) return

        maxDlyLen = hMaxDlyLen.next()
        // because we always process in before out,
        // it is crucial that the buffer be _larger_ than the `maxDlyLen`
        bufLen    = ctrl.blockSize + maxDlyLen
        buf       = tpe.newArray(bufLen)
        advance   = maxDlyLen
        bufPosIn  = maxDlyLen
        needsLen  = false
      }

      // always enter here -- `needsLen` must be `false` now
      while (true) {
        val remIn   = hIn.available
        val remOut  = min(hOut.available, min(hDlyLen.available, hDecayLen.available))

        // never be ahead more than `bufLen` frames
        val numIn = min(remIn, bufLen - advance)
        // val bufPosIn0 = bufPosIn
        if (numIn > 0) {
          val chunk = min(numIn, bufLen - bufPosIn)
          //          println(s"IN  $bufPosIn ... ${bufPosIn + chunk}")
          hIn.nextN(buf, bufPosIn, chunk)
          val chunk2 = numIn - chunk
          if (chunk2 > 0) {
            //            println(s"IN  0 ... $chunk2")
            hIn.nextN(buf, 0, chunk2)
          }
          bufPosIn  = (bufPosIn + numIn) % bufLen
          advance  += numIn
        }

        // N.B. `numOut` can be negative if `advance < maxDlyLen`
        val maxOut = if (hIn.isDone) advance else advance - maxDlyLen
        val numOut = min(remOut, maxOut) // advance - maxDlyLen)
        if (numOut > 0) {
          var i = bufPosOut + maxDlyLen
          var j = i % bufLen
          val stop = i + numOut
          while (i < stop) {
            val _dlyLen = min(maxDlyLen, hDlyLen.next())
            val _dcyLen = hDecayLen.next()
            if (dlyLen != _dlyLen || dcyLen != _dcyLen) {
              dlyLen  = _dlyLen
              dcyLen  = _dcyLen
              fbCoef  = if (_dlyLen == 0 || _dcyLen == 0d) 0d else
                exp(Log0_001 * _dlyLen / abs(dcyLen)) * signum(dcyLen)
            }
            val dlyPos  = (i - _dlyLen) % bufLen
            val v       = buf(dlyPos)
            buf(j)     += fbCoef * v
            hOut.next(v)
            i += 1
            j += 1; if (j == bufLen) j = 0
          }

/*
          // we always have to clear behind
          // to avoid dirty buffer when the input terminates
          {
            val chunk = min(numOut, bufLen - bufPosOut)
            tpe.clear(buf, bufPosOut, chunk)
            val chunk2 = numOut - chunk
            if (chunk2 > 0) {
              tpe.clear(buf, 0, chunk2)
            }
          }
*/

          bufPosOut = (bufPosOut + numOut) % bufLen
          advance  -= numOut
        }

        if (hIn.isDone && checkInDone()) return

        // N.B. `numOut` can be negative if `advance < maxDlyLen`
        if (numIn == 0 && numOut <= 0) return
      }
    }
  }
}