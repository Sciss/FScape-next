/*
 *  Histogram.scala
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

package de.sciss.fscape.stream

import akka.stream.{Attributes, FanInShape6, Inlet}
import de.sciss.fscape.stream.impl.{Handlers, StageImpl}
import de.sciss.fscape.{Util, logStream => log}
import de.sciss.numbers.Implicits._

import scala.annotation.tailrec

object Histogram {
  def apply(in: OutD, bins: OutI, lo: OutD, hi: OutD, mode: OutI, reset: OutI)(implicit b: Builder): OutI = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(in    , stage.in0)
    b.connect(bins  , stage.in1)
    b.connect(lo    , stage.in2)
    b.connect(hi    , stage.in3)
    b.connect(mode  , stage.in4)
    b.connect(reset , stage.in5)
    stage.out
  }

  private final val name = "Histogram"

  private type Shape = FanInShape6[BufD, BufI, BufD, BufD, BufI, BufI, BufI]

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape6(
      in0 = InD (s"$name.in"    ),
      in1 = InI (s"$name.bins"  ),
      in2 = InD (s"$name.lo"    ),
      in3 = InD (s"$name.hi"    ),
      in4 = InI (s"$name.mode"  ),
      in5 = InI (s"$name.reset" ),
      out = OutI(s"$name.out"   )
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  private final class Logic(shape: Shape, layer: Layer)(implicit control: Control)
    extends Handlers(name, layer, shape) {

    private[this] var histogram: Array[Int] = _
    private[this] var init    = true

    private[this] val hIn     = new Handlers.InDMain  (this, shape.in0)()
    private[this] val hBins   = new Handlers.InIAux   (this, shape.in1)(math.max(1, _))
    private[this] val hLo     = new Handlers.InDAux   (this, shape.in2)()
    private[this] val hHi     = new Handlers.InDAux   (this, shape.in3)()
    private[this] val hMode   = new Handlers.InIAux   (this, shape.in4)(_.clip(0, 1))
    private[this] val hReset  = new Handlers.InIAux   (this, shape.in5)()
    private[this] val hOut    = new Handlers.OutIMain (this, shape.out)

    override protected def stopped(): Unit = {
      super.stopped()
      histogram = null
    }

    private[this] var histogramOff: Int  = _
    private[this] var stage         = 0 // 0 -- read, 1 -- write

    protected def onDone(inlet: Inlet[_]): Unit =
      if (inlet == shape.in0 && stage == 0) {
        if (hMode.value == 0) {
          histogramOff  = 0
          stage         = 1
          process()
        } else if (hOut.flush()) {
          completeStage()
        }
      }

    @tailrec
    protected def process(): Unit = {
      log(s"$this process()")

//      if (hOut.isDone) {
//        completeStage()
//        return
//      }

      if (stage == 0) { // read
        while (stage == 0) {
          if (!(hIn.hasNext && hOut.hasNext && hReset.hasNext && hLo.hasNext && hHi.hasNext)) return

          val r = hReset.peek > 0
          if (r || init) {
            if (!(hBins.hasNext && hMode.hasNext)) return
            val bins = hBins.next()
            if (histogram == null || histogram.length != bins) {
              histogram = new Array(bins)
            } else {
              Util.clear(histogram, 0, bins)
            }
            hMode.next()
            if (init) init = false
          }
          hReset.next() // commit

          val lo    = hLo.next()
          val hi    = hHi.next()
          val bins  = histogram.length
          val binsM = bins - 1
          val in    = hIn.next()
          val inC   = in.clip(lo, hi)
          val bin   = math.min(binsM, inC.linLin(lo, hi, 0, bins).toInt)
//          println(s"histogram($bin) += 1")
          histogram(bin) += 1

          if (hMode.value == 1 || hIn.isDone) {
//            println("--> to stage 1")
//            println(histogram.mkString(", "))
            histogramOff  = 0
            stage         = 1
          }
        }

      } else {  // write
        while (stage == 1) {
          if (!hOut.hasNext) return

          var _off  = histogramOff
          val bins  = histogram.length
          hOut.next(histogram(_off))
          _off += 1
          if (_off == bins) {
            stage = 0
            if (hIn.isDone) {
              if (hOut.flush()) {
                completeStage()
                return
              }
            }
          } else {
            histogramOff = _off
          }
        }
      }

      process()
    }
  }
}