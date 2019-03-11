/*
 *  IfThenGE.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream


import akka.stream.stage.InHandler
import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{BiformFanInShape, NodeImpl, StageImpl}

import scala.collection.immutable.{Seq => ISeq}

object IfThenGE {
  /**
    * @param cases  tuples of (cond, layer, result/branch-sink)
    */
  def apply[A, E >: Null <: BufElem[A]](cases: ISeq[(OutI, Layer, Outlet[E])])(implicit b: Builder): Outlet[E] = {
    val stage0  = new Stage[A, E](b.layer, branchLayers = cases.map(_._2))
    val stage   = b.add(stage0)
    cases.zipWithIndex.foreach { case ((c, _, o), i) =>
      b.connect(c, stage.ins1(i))
      b.connect(o, stage.ins2(i))
    }
    stage.out
  }

  private final val name = "IfThenGE"

  private type Shape[A, E >: Null <: BufElem[A]] = BiformFanInShape[BufI, E, E]

  private final class Stage[A, E >: Null <: BufElem[A]](thisLayer: Layer, branchLayers: ISeq[Layer])
                                                       (implicit ctrl: Control)
    extends StageImpl[Shape[A, E]](name) {

    val shape: Shape = BiformFanInShape(
      ins1 = Vector.tabulate(branchLayers.size)(i => InI      (s"$name.cond${i+1}")),
      ins2 = Vector.tabulate(branchLayers.size)(i => Inlet[E] (s"$name.branch${i+1}")),
      out  = Outlet[E](s"$name.out")
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer = thisLayer, branchLayers = branchLayers)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](shape: Shape[A, E], layer: Layer,
                                                        branchLayers: ISeq[Layer])(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) { node =>

    override def completeAsync(): Unit = {
      branchLayers.foreach { bl =>
        ctrl.completeLayer(bl)
      }
      super.completeAsync()
    }

    private[this] val numIns    = branchLayers.size
    private[this] var pending   = numIns
    private[this] val condArr   = new Array[Boolean](numIns)
    private[this] val condDone  = new Array[Boolean](numIns)

    private class CondInHandlerImpl(in: InI, ch: Int) extends InHandler {
      def onPush(): Unit = {
        val b = grab(in)

        // println(s"IF-THEN-UNIT ${node.hashCode().toHexString} onPush($ch); numIns = $numIns, pending = $pending")

        if (b.size > 0 && !condDone(ch)) {
          condDone(ch) = true
          val v: Int = b.buf(0)
          val cond = v > 0
          condArr(ch) = cond
          pending -= 1
          // either all conditions have been evaluated,
          // or this one became true and all the previous
          // have been resolved
          if (pending == 0 || (cond && {
            var i = 0
            var prevDone = true
            while (prevDone && i <= ch) {
              prevDone &= condDone(i)
              i += 1
            }
            prevDone
          })) {
            Util.fill(condDone, 0, numIns, value = true)  // make sure the handlers won't fire twice
            process(condArr.indexOf(true))
          }
        }
        tryPull(in)
      }

      setHandler(in, this)
    }

    {
      var ch = 0
      while (ch < numIns) {
        new CondInHandlerImpl(shape.ins1(ch), ch)
        ch += 1
      }
    }

    ??? // set handlers for branchOuts

    private def process(selBranchIdx: Int): Unit = {
      logStream(s"process($selBranchIdx) $this")
      // println(s"IF-THEN-UNIT ${node.hashCode().toHexString} process($selBranchIdx)")

      var ch = 0
      val it = branchLayers.iterator
      while (ch < numIns) {
        val cond = ch == selBranchIdx
        val branchLayer = it.next()
        if (cond) {
          ctrl.launchLayer(branchLayer)
        } else {
          ctrl.completeLayer(branchLayer)
        }
        ch += 1
      }

      completeStage()
    }
  }
}
