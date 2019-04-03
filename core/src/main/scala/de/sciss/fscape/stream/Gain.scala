///*
// *  Gain.scala
// *  (FScape)
// *
// *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU Affero General Public License v3+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.fscape
//package stream
//
//import akka.stream.stage.{InHandler, OutHandler}
//import akka.stream.{Attributes, Inlet, Outlet}
//import de.sciss.fscape.stream.impl.{NodeImpl, StageImpl}
//
//import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}
//
//object Gain {
//  def apply(in: ISeq[OutD], mul: OutD, normalized: OutI, bipolar: OutI, sync: OutI)(implicit b: Builder): Vec[OutD] = {
//    val numChannels = in.size
//    val stage0      = new Stage(numChannels = numChannels, layer = b.layer)
//    val stage       = b.add(stage0)
//    b.connect(mul         , stage.inMul     )
//    b.connect(normalized  , stage.inNorm    )
//    b.connect(bipolar     , stage.inBipolar )
//    b.connect(sync        , stage.inSync    )
//
//    (in zip stage.ins).foreach { case (i, j) => b.connect(i, j) }
//
//    stage.outs
//  }
//
//  private final val name = "Gain"
//
//  private final case class Shape(inMul: InD, inNorm: InI, inBipolar: InI, inSync: InI, ins: Vec[InD], outs: Vec[OutD])
//    extends akka.stream.Shape {
//
//    val inlets  : Vec[Inlet [_]] = inMul +: inNorm +: inBipolar +: inSync +: ins
//    val outlets : Vec[Outlet[_]] = outs
//
//    def deepCopy(): Shape = {
//      Shape(inMul = inMul.carbonCopy(), inNorm = inNorm.carbonCopy(), inBipolar = inBipolar.carbonCopy(),
//        inSync = inSync.carbonCopy(), ins = ins.map(_.carbonCopy()), outs = outs.map(_.carbonCopy()))
//    }
//  }
//
//  private final class Stage(numChannels: Int, layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
//    val shape = Shape(
//      inMul     = InD(s"$name.mul"        ),
//      inNorm    = InI(s"$name.normalized" ),
//      inBipolar = InI(s"$name.bipolar"    ),
//      inSync    = InI(s"$name.sync"       ),
//      ins       = Vector.tabulate(numChannels)(ch => InD  (s"$name.in${ ch+1}")),
//      outs      = Vector.tabulate(numChannels)(ch => OutD (s"$name.out${ch+1}")),
//    )
//
//    def createLogic(attr: Attributes) = new Logic(shape, layer)
//  }
//
//  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
//    extends NodeImpl(name, layer, shape) { self =>
//
//    private[this] val numChannels = shape.ins.size
//
//    private[this] var mul       : Double  = _
//    private[this] var add       : Double  = _
//    private[this] var normalized: Boolean = _
//    private[this] var bipolar   : Boolean = _
//    private[this] var sync      : Boolean = _
//    private[this] val channels  = Array.tabulate(numChannels)(new MainChannel(_))
//
//    private[this] var auxRemain = 4 // mul, normalized, bipolar, sync
//    private[this] var auxReady  = false
//
//    // clean up
//
//    override protected def stopped(): Unit = {
//      super.stopped()
//      channels.foreach(_.stopped())
//    }
//
//    // aux handling
//
//    private def decreaseAuxRemain(): Unit = {
//      auxRemain -= 1
//      if (auxRemain == 0) {
//        auxReady = true
//        channels.foreach(_.notifyAuxReady())
//      }
//    }
//
//    private class AuxHandler[A, E <: BufElem[A]](set: A => Unit)(in: Inlet[E]) extends InHandler {
//      private[this] var done  = false
//
//      override def toString: String = in.toString
//
//      def onPush(): Unit = {
//        logStream(s"onPush() $this")
//        val b = grab(in)
//        if (!done && b.size > 0) {
//          val v = b.buf(0)
//          done  = true
//          set(v)
//          decreaseAuxRemain()
//        }
//        b.release()
//      }
//
//      override def onUpstreamFinish(): Unit = {
//        logStream(s"onUpstreamFinish() $this")
//        if (!done) super.onUpstreamFinish()
//      }
//
//      setHandler(in, this)
//    }
//
//    // input / output
//
//    private class MainChannel(ch: Int) extends InHandler with OutHandler {
//      override def toString: String = s"$self channel $ch" // shape.ins(ch).toString
//
//      private[this] var fileBuf: FileBuffer = _
//
//      private[this] val in  = shape.ins (ch)
//      private[this] val out = shape.outs(ch)
//
//      var done = false
//
//      var minValue: Double = Double.PositiveInfinity
//      var maxValue: Double = Double.NegativeInfinity
//
//      var normFileBufReady = false
//
//      private[this] var minMaxValid = false
//
//      def stopped(): Unit =
//        disposeNoFire()
//
//      private def disposeNoFire(): Unit =
//        if (fileBuf != null) {
//          fileBuf.dispose()
//          fileBuf = null
//        }
//
//      private def dispose(): Unit = {
//        done = true
//        disposeNoFire()
//        checkAllClosed()
//      }
//
//      def onPush(): Unit = {
//        logStream(s"onPush() $this")
//        inPushed()
//      }
//
//      override def onUpstreamFinish(): Unit = {
//        logStream(s"onUpstreamFinish() $this")
//        inClosed()
//      }
//
//      def onPull(): Unit = {
//        logStream(s"onPull() $this")
//        outPulled()
//      }
//
//      override def onDownstreamFinish(): Unit = {
//        logStream(s"onDownstreamFinish() $this")
//        outClosed()
//      }
//
//      private def inPushed(): Unit = if (auxReady) {
//        if (normalized) {
//          assert (!normFileBufReady)
//          val b = grab(in)
//          fileBuf.write(b.buf, 0, b.size)
//          analyzeNorm(b)
//          b.release()
//          if (isClosed(in)) {
//            inClosed()  // may start rewinding file-buf etc.
//          } else {
//            pull(in)
//          }
//
//        } else {
//          if (isAvailable(out)) {
//            pipeDirect()
//          }
//        }
//      }
//
//      private def analyzeNorm(b: BufD): Unit = {
//        val buf   = b.buf
//        var i     = 0
//        val sz    = b.size
//        var _min  = minValue
//        var _max  = maxValue
//        while (i < sz) {
//          val v = buf(i)
//          if (v < _min) _min = v
//          if (v > _max) _max = v
//          i += 1
//        }
//        minValue = _min
//        maxValue = _max
//      }
//
//      private def inClosed(): Unit = {
//        if (isAvailable(in)) {
//          inPushed()
//
//        } else {
//          if (auxReady && normalized) {
//            if (!normFileBufReady) {
//              normFileBufReady = true
//              fileBuf.rewind()
//              if (sync) {
//                syncCheckAllFileBuffersReady()
//              } else {
//                notifyMinMaxValid()
//              }
//            }
//          } else {
//            done = true
//            checkAllClosed()
//          }
//        }
//      }
//
//      def notifyMinMaxValid(): Unit = {
//        minMaxValid = true
//        add   = if (bipolar) 0.0 else -minValue
//        mul   = if (bipolar) {
//          val ceil = math.max(math.abs(minValue), math.abs(minValue))
//          if (ceil > 0.0) mul/ceil else 0.0
//
//        } else {
//          val span = maxValue - minValue
//          if (span > 0.0) mul/span else 0.0
//        }
//        checkPipeNorm()
//      }
//
//      def notifyAuxReady(): Unit = {
//        if (isAvailable(in  )) inPushed()
//        if (isAvailable(out )) outPulled()
//      }
//
//      private def checkPipeNorm(): Unit = {
//        if (isAvailable(out) && !done) {
//          done = pipeNorm()
//          if (done) dispose()
//        }
//      }
//
//      private def outPulled(): Unit = if (auxReady) {
//        if (normalized) {
//          if (minMaxValid) {
//            checkPipeNorm()
//          }
//
//        } else {
//          if (isAvailable(in)) {
//            pipeDirect()
//          }
//        }
//      }
//
//      private def outClosed(): Unit =
//        dispose()
//
//      private def pipeNorm(): Boolean = {
//        val rem = fileBuf.numFrames - fileBuf.position
//        if (rem == 0L) return true
//
//        val b     = ctrl.borrowBufD()
//        val chunk = math.min(rem, b.size).toInt
//        b.size    = chunk
//        fileBuf.read(b.buf, 0, chunk)
//        if (add != 0.0) Util.add(b.buf, 0, chunk, add)
//        if (mul != 1.0) Util.mul(b.buf, 0, chunk, mul)
//        push(out, b)
//        false
//      }
//
//      private def pipeDirect(): Unit = {
//        val b = grab(in)
//        Util.mul(b.buf, 0, b.size, mul)
//        push(out, b)
//        tryPull(in)
//      }
//
//      setHandler(in , this)
//      setHandler(out, this)
//    }
//
//    // setup
//
//    new AuxHandler((v: Double)  => mul        = v     )(shape.inMul     )
//    new AuxHandler((v: Int)     => normalized = v != 0)(shape.inNorm    )
//    new AuxHandler((v: Int)     => bipolar    = v != 0)(shape.inBipolar )
//    new AuxHandler((v: Int)     => sync       = v != 0)(shape.inSync    )
//
//    // ...
//
//    private def checkAllClosed(): Unit = {
//      val numClosed = channels.count(_.done)
//      if (numClosed == numChannels) completeStage()
//    }
//
//    private def syncCheckAllFileBuffersReady(): Unit = {
//      assert (sync)
//
//      var numClosed = 0
//      var numReady  = 0
//
//      channels.foreach { h =>
//        if (h.done) {
//          numClosed += 1
//          numReady  += 1
//        } else if (h.normFileBufReady) {
//          numReady  += 1
//        }
//      }
//
//      if      (numClosed == numChannels) completeStage()
//      else if (numReady  == numChannels) {
//
//        var allMin = Double.PositiveInfinity
//        var allMax = Double.NegativeInfinity
//
//        channels.foreach { h =>
//          allMin  = math.min(allMin, h.minValue)
//          allMax  = math.max(allMax, h.maxValue)
//        }
//
//        channels.foreach { h =>
//          h.minValue  = allMin
//          h.maxValue  = allMax
//          if (!h.done) {
//            h.notifyMinMaxValid()
//          }
//        }
//      }
//    }
//  }
//}