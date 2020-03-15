/*
 *  IfThenGE.scala
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


import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import de.sciss.fscape.stream.impl.{BiformShape, NodeImpl, StageImpl}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}
import scala.concurrent.Future

object IfThenGE {
  /**
    * @param cases  tuples of (cond, layer, result/branch-sink)
    *               the branch-sinks must all have the same size, equal to `numOutputs`
    */
  def apply[A, E >: Null <: BufElem[A]](numOutputs: Int, cases: ISeq[(OutI, Layer, Vec[Outlet[E]])])
                                       (implicit b: Builder): Vec[Outlet[E]] = {
    // we insert dummy pipe elements here and replace thus the cases' outlets.
    // that way we ensure that we have elements placed in the correct layer, even
    // if the branch just references outer layers. also, the branch-logic then
    // registers itself in the correct layer and can be shut down.
    val stage0  = new Stage[A, E](numOutputs = numOutputs, thisLayer = b.layer, branchLayers = cases.map(_._2))
    val stage   = b.add(stage0)
    cases.zipWithIndex.foreach { case ((cond, branchLayer, branchOutSeq), bi) =>
      require (branchOutSeq.size == numOutputs, s"${branchOutSeq.size} != $numOutputs")
      b.connect(cond, stage.ins1(bi))
      branchOutSeq.zipWithIndex.foreach { case (branchOut, ch) =>
        val name    = if (numOutputs == 1) s"Branch(${bi+1})" else s"Branch(${bi+1},${ch + 1})"
        val idx2    = bi * numOutputs + ch
        val bStage0 = new BranchStage[E](name, branchLayer)
        val bStage  = b.add(bStage0)
        b.connect(branchOut , bStage.in)
        b.connect(bStage.out, stage.ins2(idx2))
      }
    }
    stage.outlets.toIndexedSeq
  }

  private final val name = "IfThenGE"

  private type Shape[A, E >: Null <: BufElem[A]] = BiformShape[BufI, E, E]

  private type BranchShape[A] = FlowShape[A, A]

  private final class Stage[A, E >: Null <: BufElem[A]](numOutputs: Int,
                                                        thisLayer: Layer, branchLayers: ISeq[Layer])
                                                       (implicit ctrl: Control)
    extends StageImpl[Shape[A, E]](name) {

    val shape: Shape = BiformShape(
      ins1 = Vector.tabulate(branchLayers.size)(i => InI(s"$name.cond${i+1}")),
      ins2 = Vector.tabulate(branchLayers.size * numOutputs) { i =>
        val branchIdx = i / numOutputs
        val channel   = i % numOutputs
        val inletName = if (numOutputs == 1) s"$name.branch${i+1}" else s"$name.branch${branchIdx+1}_${channel + 1}"
        Inlet[E](inletName)
      },
      outlets = Vector.tabulate(numOutputs)(i => Outlet[E](s"$name.out${i+1}"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic[A, E](numOutputs = numOutputs, shape = shape, layer = thisLayer, branchLayers = branchLayers)
  }

  private final class BranchStage[A](name: String, layer: Layer)(implicit control: Control)
    extends StageImpl[BranchShape[A]](name) {

    val shape: Shape = FlowShape[A, A](
      in  = Inlet [A](s"$name.in"),
      out = Outlet[A](s"$name.out")
    )

    def createLogic(inheritedAttributes: Attributes): NodeImpl[Shape] = new BranchLogic[A](name, layer, shape)
  }

  // A simple no-op pipe, so we have a handle we can shut down
  private final class BranchLogic[A](name: String, layer: Layer, shape: BranchShape[A])(implicit control: Control)
    extends NodeImpl(name, layer, shape) with InHandler with OutHandler { self =>

    override def toString = s"$name@${hashCode().toHexString}"

    private def pump(): Unit = {
      val a = grab(shape.in)
      push(shape.out, a)
      if (!isClosed(shape.in)) {
        pull(shape.in)
      } else {
        completeStage()
      }
    }

    override def onPush(): Unit = {
      val ok = isAvailable(shape.out)
      logStream(s"$this - onPush() $ok")
      if (ok) {
        pump()
      }
    }

    override def onPull(): Unit = {
      val ok = isAvailable(shape.in)
      logStream(s"$this - onPull() $ok")
      if (ok) {
        pump()
      }
    }

    override def onUpstreamFinish(): Unit = {
      logStream(s"$this - onUpstreamFinish")
      if (!isAvailable(shape.in)) super.onUpstreamFinish()
    }

    override def onDownstreamFinish(cause: Throwable): Unit = {
      logStream(s"$this - onDownstreamFinish")
      super.onDownstreamFinish(cause)
    }

    setHandlers(shape.in, shape.out, this)
  }

  private final class Logic[A, E >: Null <: BufElem[A]](numOutputs: Int, shape: Shape[A, E], layer: Layer,
                                                        branchLayers: ISeq[Layer])(implicit ctrl: Control)
    extends NodeImpl(name, layer, shape) { self =>

    private[this] val numBranches   = branchLayers.size
    private[this] var condPending   = numBranches   // missing for all conditions to have been presented
    private[this] val condArr       = new Array[Boolean](numBranches)
    private[this] val condDone      = new Array[Boolean](numBranches)
    private[this] var selBranchIdx  = -1
    private[this] var selIn : Array[Inlet [E]] = null
    private[this] val outs  : Array[Outlet[E]] = shape.outlets.toArray
    private[this] var selBranchChans = numOutputs  // number of selected branch channels alive

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

    private class CondInHandlerImpl(in: InI, branchIdx: Int) extends InHandler {

      override def toString: String = s"$self.CondInHandlerImpl($in)"

      def onPush(): Unit = {
        logStream(s"onPush() $self.${in.s}")
        val b = grab(in)

        // println(s"IF-THEN-UNIT ${node.hashCode().toHexString} onPush($ch); numIns = $numIns, pending = $pending")

        if (b.size > 0 && !condDone(branchIdx)) {
          condDone(branchIdx) = true
          val v: Int = b.buf(0)
          b.release()
          val cond = v != 0
          condArr(branchIdx) = cond
          condPending -= 1
          // logStream(s"condDone($ch). pending = $pending")

          // either all conditions have been evaluated,
          // or this one became true and all the previous
          // have been resolved
          if (condPending == 0 || (cond && {
            var i = 0
            var prevDone = true
            while (prevDone && i <= branchIdx) {
              prevDone &= condDone(i)
              i += 1
            }
            prevDone
          })) {
            Util.fill(condDone, 0, numBranches, value = true)  // make sure the handlers won't fire twice
            selBranchIdx  = condArr.indexOf(true)
            condPending       = 0
            if (selBranchIdx >= 0) {
              selIn = Array.tabulate(numOutputs)(ch => shape.ins2(selBranchIdx * numOutputs + ch))
            }
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
        if (!condDone(branchIdx) && selBranchIdx < 0) {
          completeAll()
          super.onUpstreamFinish()
        }
      }

      setHandler(in, this)
    }

    private class BranchInHandlerImpl(in: Inlet[E], branchIdx: Int, ch: Int) extends InHandler {
      override def toString: String = s"$self.BranchInHandlerImpl($in)"

      private[this] val out = outs(ch)

      def onPush(): Unit = {
        logStream(s"onPush() $self.${in.s}")
        if (branchIdx == selBranchIdx) {
          if (isAvailable(out) || isClosed(out)) {
            pump(ch)
          }

        } else {
          // should not happen, but if so, just discard
          val b = grab(in)
          b.release()
        }
      }

      override def onUpstreamFinish(): Unit = {
        logStream(s"onUpstreamFinish() $self.${in.s}")
        if (branchIdx == selBranchIdx && !isAvailable(in)) {
          selBranchChans -= 1
          if (selBranchChans == 0) {
            // N.B.: we do not shut down the branches,
            // because it's their business if they want
            // to keep running sinks after the branch
            // output signal finishes.
            //
            // completeAll()
            super.onUpstreamFinish()
          }
        }
      }

      setHandler(in, this)
    }

    private final class OutHandlerImpl(ch: Int) extends OutHandler {
      override def toString: String = s"$self.OutHandlerImpl($ch)"

      def onPull(): Unit = {
        logStream(s"onPull() $self")
        if (selIn != null && isAvailable(selIn(ch))) {
          pump(ch)
        }
      }

      // N.B.: see BranchInHandlerImpl for the same reasons (?)

      override def onDownstreamFinish(cause: Throwable): Unit = {
        val all = shape.outlets.forall(isClosed(_)) // IntelliJ highlight bug
        logStream(s"onDownstreamFinish() $self - $all")
        if (all) {
          completeAll()
          super.onDownstreamFinish(cause)
        }
      }

      setHandler(outs(ch), this)
    }

    private def completeAll(): Unit = {
      logStream(s"completeAll() $self")
      var ch = 0
      val it = branchLayers.iterator
      while (ch < numBranches) {
        val branchLayer = it.next()
        ctrl.completeLayer(branchLayer)
        ch += 1
      }
    }

    {
      var bi = 0
      while (bi < numBranches) {
        new CondInHandlerImpl(shape.ins1(bi), bi)
        var ch = 0
        while (ch < numOutputs) {
          val idx2 = bi * numOutputs + ch
          new BranchInHandlerImpl(shape.ins2(idx2), branchIdx = bi, ch = ch)
          ch += 1
        }
        bi += 1
      }
      var ch = 0
      while (ch < numOutputs) {
        new OutHandlerImpl(ch)
        ch += 1
      }
    }

    private def pump(ch: Int): Unit = {
      logStream(s"pump($ch) $self")
      val _selIn = selIn(ch)
      val b = grab(_selIn)
      val _out = outs(ch)
      if (isClosed(_out)) {
        b.release()
      } else {
        push(outs(ch), b)
      }
      if (isClosed(_selIn)) {
        // N.B.: see BranchInHandlerImpl for the same reasons
        //
        // ctrl.completeLayer(branchLayers(selBranchIdx))
        completeStage()
      } else {
        tryPull(_selIn)
      }
    }

    private def branchSelected(): Unit = {
      logStream(s"branchSelected($selBranchIdx) $self")
      // println(s"IF-THEN-UNIT ${node.hashCode().toHexString} process($selBranchIdx)")

      var bi = 0
      val it = branchLayers.iterator
      var done: Future[Unit] = null
      while (bi < numBranches) {
        val cond = bi == selBranchIdx
        val branchLayer = it.next()
        if (cond) {
          // we set `done` here which completes as
          // the launch is complete.
          done = ctrl.launchLayer(branchLayer)
        } else {
          ctrl.completeLayer(branchLayer)
        }
        bi += 1
      }

      if (selIn == null) completeStage()
      else {
        var ch = 0
        while (ch < numOutputs) {
          val in  = selIn (ch)
          val out = outs  (ch)
          if (isClosed(in)) {
            completeStage()
          } else if (isAvailable(in) && isAvailable(out)) {
            // either we can immediately grab data...
            pump(ch)
          } else if (done != null) {
            // ...or we have to wait for the launch to be complete,
            // and then try to pull the branch output.
            val async = getAsyncCallback { _: Unit =>
              val hbp = hasBeenPulled(in)
              // logStream(s"launchLayer done (2/2) - has been pulled? $hbp - $self")
              if (!hbp) tryPull(in)
            }
            import ctrl.config.executionContext
            done.foreach { _ =>
              logStream(s"launchLayer done - $self")
              async.invoke(())
            }
          }

          ch += 1
        }
      }
    }
  }
}
