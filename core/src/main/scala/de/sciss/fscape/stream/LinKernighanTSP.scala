/*
 *  LinKernighanTSP.scala
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

import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers._
import de.sciss.fscape.stream.impl.shapes.In5Out2Shape
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.{logStream => log}
import de.sciss.tsp.LinKernighan

import scala.annotation.tailrec

object LinKernighanTSP {
  def apply(init: OutI, weights: OutD, size: OutI, mode: OutI, timeOut: OutD)(implicit b: Builder): (OutI, OutD) = {
    val stage0  = new Stage(b.layer)
    val stage   = b.add(stage0)
    b.connect(init    , stage.in0)
    b.connect(weights , stage.in1)
    b.connect(size    , stage.in2)
    b.connect(mode    , stage.in3)
    b.connect(timeOut , stage.in4)
    (stage.out0, stage.out1)
  }

  private final val name = "LinKernighanTSP"

  private type Shp = In5Out2Shape[BufI, BufD, BufI, BufI, BufD,   BufI, BufD]

  private final class Stage(layer: Layer)(implicit ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = In5Out2Shape(
      in0     = InI (s"$name.init"    ),
      in1     = InD (s"$name.weights" ),
      in2     = InI (s"$name.size"    ),
      in3     = InI (s"$name.mode"    ),
      in4     = InD (s"$name.timeOut" ),
      out0    = OutI(s"$name.tour"    ),
      out1    = OutD(s"$name.cost"    ),
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer)
  }

  private final class Logic(shape: Shp, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape) {

    private[this] val hInit   : InIMain   = InIMain  (this, shape.in0)
    private[this] val hWeights: InDMain   = InDMain  (this, shape.in1)
    private[this] val hSize   : InIAux    = InIAux   (this, shape.in2)(math.max(1, _)) // or should it be 2 ?
    private[this] val hMode   : InIAux    = InIAux   (this, shape.in3)() // not used
    private[this] val hTimeOut: InDAux    = InDAux   (this, shape.in4)() // not used
    private[this] val hOutTour: OutIMain  = OutIMain (this, shape.out0)
    private[this] val hOutCost: OutDMain  = OutDMain (this, shape.out1)

    private[this] var outTour: Array[Int] = _
    private[this] var outCost: Double     = _
    private[this] var outTourOff          = 0

    private[this] var stage               = 0   // 0 -- read size, 1 -- read init and weights, 2 -- write

    private[this] var size        = 0
    private[this] var tour0   : Array[Int]            = _
    private[this] var weights : Array[Array[Double]]  = _
    private[this] var tour0Off    = 0
    private[this] var tour0Rem    = 0
    private[this] var outTourRem  = 0
    private[this] var outCostRem  = false
    private[this] var weightsOffA = 0
    private[this] var weightsOffB = 0
    private[this] var weightsRem  = 0

    override protected def stopped(): Unit = {
      super.stopped()
      outTour = null
      tour0   = null
      weights = null
    }

    private def hotInsDone(): Boolean = {
      val res = hOutTour.flush() & hOutCost.flush()
      if (res) {
        completeStage()
      }
      res
    }

    protected def onDone(inlet: Inlet[_]): Unit =
      if (inlet == shape.in0) {
        if (stage == 0 || (stage == 1 && tour0Rem > 0)) hotInsDone()
      } else if (inlet == shape.in1) {
        if (stage == 0 || (stage == 1 && outCostRem))   hotInsDone()
      }

    override protected def onDone(outlet: Outlet[_]): Unit =
      if (hOutTour.isDone && hOutCost.isDone) {
        completeStage()
      } else if (outlet == shape.out0) {
        if (stage == 2 && outTourRem > 0) {
          outTourRem = 0
          process()
        }
      } else if (outlet == shape.out1) {
        if (stage == 2 && outCostRem) {
          outCostRem = false
          process()
        }
      }

    @tailrec
    protected def process(): Unit = {
      log(s"$this process()")

      //      if (hOutTour.isDone && hOutCost.isDone) {
      //        completeStage()
      //        return
      //      }

      if (stage == 0) { // read size
        if (!hSize.hasNext) return

        size = hSize.next()
        if (tour0 == null || tour0.length != size) {
          tour0   = new Array(size)
          weights = Array.ofDim(size, size)
        }
        tour0Off    = 0
        tour0Rem    = size
        weightsOffA = 0
        weightsOffB = 1
        weightsRem  = size * (size - 1) / 2

        if (hMode   .hasNext) hMode   .next() // ignore
        if (hTimeOut.hasNext) hTimeOut.next() // ignore
        stage = 1

      } else if (stage == 1) {  // read init and weights
        while (stage == 1) {
          if ((tour0Rem == 0 || !hInit.hasNext) && (weightsRem == 0 || !hWeights.hasNext)) return

          while (tour0Rem > 0 && hInit.hasNext) {
            tour0(tour0Off) = hInit.next()
            tour0Off += 1
            tour0Rem -= 1
          }

          while (weightsRem > 0 && hWeights.hasNext) {
            val w = hWeights.next()
            weights(weightsOffA)(weightsOffB) = w
            weights(weightsOffB)(weightsOffA) = w
            weightsOffB += 1
            if (weightsOffB == size) {
              weightsOffA  += 1
              weightsOffB   = weightsOffA + 1
            }
            weightsRem -= 1
          }

          if (tour0Rem == 0 && weightsRem == 0) {
            // assert (weightsOffA == size - 1, weightsOffA.toString)
            val lk = LinKernighan(edgeWeights = weights, tour0 = tour0)
            lk.run()  // XXX TODO --- should we allow for time-out?
            outTour     = lk.tour
            outCost     = lk.tourCost
            outTourOff  = 0
            outTourRem  = if (hOutTour.isDone) 0 else size
            outCostRem  = !hOutCost.isDone // true
            stage       = 2
          }
        }

      } else {  // write
        while (stage == 2) {
          if ((outTourRem == 0 || !hOutTour.hasNext) && (!outCostRem || !hOutCost.hasNext)) return

          while (outTourRem > 0 && hOutTour.hasNext) {
            hOutTour.next(outTour(outTourOff))
            outTourOff += 1
            outTourRem -= 1
          }

          if (outCostRem && hOutCost.hasNext) {
            hOutCost.next(outCost)
            outCostRem = false
          }

          if (outTourRem == 0 && !outCostRem) {
            stage = 0
            if (hInit.isDone || hWeights.isDone) {
              if (hotInsDone()) return
            }
          }
        }
      }

      process()
    }
  }
}
