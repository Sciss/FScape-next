/*
 *  IfThenUnit.scala
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

import akka.stream.Attributes
import akka.stream.stage.InHandler
import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl, UniformSinkShape}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.Future

object IfThenUnit {
  /**
    * @param cases  tuples of (cond, layer)
    */
  def apply(cases: ISeq[(OutI, Layer)])(implicit b: Builder): Unit = {
    val stage0  = new Stage(b.layer, branchLayers = cases.map(_._2))
    val stage   = b.add(stage0)
    cases.zipWithIndex.foreach { case (c, i) =>
      b.connect(c._1, stage.inlets(i))
    }
  }

  private final val name = "IfThenUnit"

  private type Shp = UniformSinkShape[BufI]

  private final class Stage(thisLayer: Layer, branchLayers: ISeq[Layer])(implicit ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = UniformSinkShape(
      Vector.tabulate(branchLayers.size)(i => InI(s"$name.cond${i+1}"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = thisLayer, branchLayers = branchLayers)
  }

  private final class Logic(shape: Shp, layer: Layer, branchLayers: ISeq[Layer])(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) { node =>

    override def completeAsync(): Future[Unit] = {
      val futBranch = branchLayers.map { bl =>
        ctrl.completeLayer(bl)
      }
      val futSuper = super.completeAsync()
      import ctrl.config.executionContext
      Future.sequence(futBranch :+ futSuper).map(_ => ())
    }

    private[this] val numIns    = branchLayers.size
    private[this] var pending   = numIns
    private[this] val condArr   = new Array[Boolean](numIns)
    private[this] val condDone  = new Array[Boolean](numIns)

    private class _InHandlerImpl(in: InI, ch: Int) extends InHandler {
      def onPush(): Unit = {
        val b = grab(in)

        // println(s"IF-THEN-UNIT ${node.hashCode().toHexString} onPush($ch); numIns = $numIns, pending = $pending")

        if (b.size > 0 && !condDone(ch)) {
          condDone(ch) = true
          val v: Int = b.buf(0)
          val cond = v != 0
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
        new _InHandlerImpl(shape.inlets(ch), ch)
        ch += 1
      }
    }

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
