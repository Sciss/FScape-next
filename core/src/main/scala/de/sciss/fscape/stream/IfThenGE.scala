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


import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{BiformFanInShape, NodeImpl, StageImpl}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.Future

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
    extends NodeImpl(name, layer, shape) { self =>

    private[this] val numIns        = branchLayers.size
    private[this] var pending       = numIns
    private[this] val condArr       = new Array[Boolean](numIns)
    private[this] val condDone      = new Array[Boolean](numIns)
    private[this] var selBranchIdx  = -1
    private[this] var selBranch: Inlet[E] = null
    private[this] val out           = shape.out

    override def completeAsync(): Future[Unit] = {
      val futBranch = branchLayers.map { bl =>
        ctrl.completeLayer(bl)
      }
      val futSuper = super.completeAsync()
      import ctrl.config.executionContext
      Future.sequence(futBranch :+ futSuper).map(_ => ())
    }

    // only poll the condition inlets
    override protected def launch(): Unit = {
      logStream(s"$this - launch")
      shape.ins1.foreach(pull)
    }

    private class CondInHandlerImpl(in: InI, ch: Int) extends InHandler {

      override def toString: String = s"$self.CondInHandlerImpl($in)"

      def onPush(): Unit = {
        logStream(s"onPush() $self.${in.s}")
        val b = grab(in)

        // println(s"IF-THEN-UNIT ${node.hashCode().toHexString} onPush($ch); numIns = $numIns, pending = $pending")

        if (b.size > 0 && !condDone(ch)) {
          condDone(ch) = true
          val v: Int = b.buf(0)
          b.release()
          val cond = v > 0
          condArr(ch) = cond
          pending -= 1
          // logStream(s"condDone($ch). pending = $pending")

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
            selBranchIdx  = condArr.indexOf(true)
            pending       = 0
            if (selBranchIdx >= 0) selBranch = shape.ins2(selBranchIdx)
            branchSelected()
          }
        } else {
          b.release()
        }

        tryPull(in)
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish() $self.${in.s}")
        // if we made the decision, ignore,
        // otherwise shut down including branches
        if (selBranchIdx < 0) {
          completeAll()
          super.onUpstreamFinish()
        }
      }

      setHandler(in, this)
    }

    private class BranchInHandlerImpl(in: Inlet[E], ch: Int) extends InHandler {
      override def toString: String = s"$self.BranchInHandlerImpl($in)"

      def onPush(): Unit = {
        logStream(s"onPush() $self.${in.s}")
        if (ch == selBranchIdx) {
          if (isAvailable(out)) {
            pump()
          }

        } else {
          // should not happen, but if so, just discard
          val b = grab(in)
          b.release()
        }
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish() $self.${in.s}")
        if (ch == selBranchIdx && !isAvailable(in)) {
          // N.B.: we do not shut down the branches,
          // because it's their business if they want
          // to keep running sinks after the branch
          // output signal finishes.
          //
          // completeAll()
          super.onUpstreamFinish()
        }
      }

      setHandler(in, this)
    }

    private object OutHandlerImpl extends OutHandler {
      override def toString: String = s"$self.OutHandlerImpl"

      def onPull(): Unit = {
        logStream(s"onPull() $self")
        if (selBranch != null && isAvailable(selBranch)) {
          pump()
        }
      }

      // N.B.: see BranchInHandlerImpl for the same reasons

//      override def onDownstreamFinish(): Unit = {
//        completeAll()
//        super.onDownstreamFinish()
//      }
    }

    private def completeAll(): Unit = {
      logStream(s"completeAll() $self")
      var ch = 0
      val it = branchLayers.iterator
      while (ch < numIns) {
        val branchLayer = it.next()
        ctrl.completeLayer(branchLayer)
        ch += 1
      }
    }

    {
      var ch = 0
      while (ch < numIns) {
        new CondInHandlerImpl   (shape.ins1(ch), ch)
        new BranchInHandlerImpl (shape.ins2(ch), ch)
        ch += 1
      }
      setHandler(out, OutHandlerImpl)
    }

    private def pump(): Unit = {
      logStream(s"pump() $self")
      val b = grab(selBranch)
      push(out, b)
      if (isClosed(selBranch)) {
        // N.B.: see BranchInHandlerImpl for the same reasons
        //
        // ctrl.completeLayer(branchLayers(selBranchIdx))
        completeStage()
      } else {
        tryPull(selBranch)
      }
    }

    private def branchSelected(): Unit = {
      logStream(s"branchSelected($selBranchIdx) $self")
      // println(s"IF-THEN-UNIT ${node.hashCode().toHexString} process($selBranchIdx)")

      var ch = 0
      val it = branchLayers.iterator
      var done: Future[Unit] = null
      while (ch < numIns) {
        val cond = ch == selBranchIdx
        val branchLayer = it.next()
        if (cond) {
          // we set `done` here which completes as
          // the launch is complete.
          done = ctrl.launchLayer(branchLayer)
        } else {
          ctrl.completeLayer(branchLayer)
        }
        ch += 1
      }

      if (selBranch == null || isClosed(selBranch)) {
        completeStage()
      } else if (isAvailable(selBranch) && isAvailable(out)) {
        // either we can immediately grab data...
        pump()
      } else if (done != null) {
        // ...or we have to wait for the launch to be complete,
        // and then try to pull the branch output.
        val async = getAsyncCallback { _: Unit =>
          val hbp = hasBeenPulled(selBranch)
          logStream(s"launchLayer done (2/2) - has been pulled? $hbp - $self")
          if (!hbp) tryPull(selBranch)
        }
        import ctrl.config.executionContext
        done.foreach { _ =>
          logStream(s"launchLayer done (1/2) - $self")
          async.invoke(())
        }
      }
    }
  }
}
