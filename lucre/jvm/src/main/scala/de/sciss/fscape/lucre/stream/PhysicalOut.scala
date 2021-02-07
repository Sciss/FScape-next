/*
 *  PhysicalOut.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre.stream

import akka.stream.Attributes
import akka.stream.stage.InHandler
import de.sciss.fscape.Log.{stream => logStream}
import de.sciss.fscape.stream.impl.shapes.UniformSinkShape
import de.sciss.fscape.stream.impl.{NodeHasInitImpl, NodeImpl, StageImpl}
import de.sciss.fscape.stream.{BufD, Builder, Control, InD, Layer, OutD}
import de.sciss.lucre.Disposable
import de.sciss.lucre.synth.{RT, Server}
import de.sciss.numbers
import de.sciss.proc.AuralSystem

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.TxnExecutor

object PhysicalOut {
  def apply(in: ISeq[OutD], auralSystem: AuralSystem)(implicit b: Builder): Unit = {
    val sink = new Stage(layer = b.layer, numChannels = in.size, auralSystem = auralSystem)
    val stage = b.add(sink)
    (in zip stage.inlets).foreach { case (output, input) =>
      b.connect(output, input)
    }
  }

  private final val name = "PhysicalOut"

  private type Shp = UniformSinkShape[BufD]

  private final class Stage(layer: Layer, numChannels: Int, auralSystem: AuralSystem)
                           (implicit protected val ctrl: Control)
    extends StageImpl[Shp](name) {

    val shape: Shape = UniformSinkShape[BufD](
      Vector.tabulate(numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(shape, layer = layer, numChannels = numChannels,
        auralSystem = auralSystem)
  }

  private final class Logic(shape: Shp, layer: Layer, numChannels: Int, auralSystem: AuralSystem)
                           (implicit ctrl: Control)
    extends NodeImpl[Shp](name, layer, shape)
      with NodeHasInitImpl { logic =>

    //    private[this] var buf     : io.Frames = _

    private[this] var pushed        = 0
    private[this] val bufIns        = new Array[BufD](numChannels)

    private[this] var shouldStop    = false
    private[this] var _isSuccess    = false

    private[this] val circleSize    = 3
    private[this] val rtBufCircle   = Array.fill(circleSize)(new Array[Array[Float]](numChannels))
    private[this] var writtenCircle = 0
    private[this] var readCircle    = 0
    private[this] var readCircleRT  = 0
    private[this] var obsAural : Disposable[RT] = _

    private[this] var LAST_REP_PROC   = 0L
    private[this] var LAST_REP_PROC_C = 0
    private[this] var LAST_REP_IN     = 0L
    private[this] var LAST_REP_IN_C   = 0

    private[this] val DEBUG = false
    private[this] var okRT  = false

    {
      val ins = shape.inlets
      var ch = 0
      while (ch < numChannels) {
        val in = ins(ch)
        setHandler(in, new InH(in /* , ch */))
        ch += 1
      }
    }

    private final class InH(in: InD /* , ch: Int */) extends InHandler {
      def onPush(): Unit = {
        pushed += 1
        if (canProcess) process()
      }

      override def onUpstreamFinish(): Unit =
        if (isAvailable(in)) {
          shouldStop = true
        } else {
          logStream.info(s"onUpstreamFinish($in)")
          _isSuccess = true
          super.onUpstreamFinish()
        }
    }

    // ---- StageLogic

    override protected def init(): Unit = {
      // super.init()
      logStream.info(s"$this - init()")
    }

    private def atomic[A](fun: RT => A): A = TxnExecutor.defaultAtomic { implicit itx =>
      implicit val rt: RT = RT.wrap(itx)
      fun(rt)
    }

    // N.B.: not on the Akka thread
    private def startRT(s: Server)(implicit tx: RT): Unit = {
      val bufSizeS = {
        val bufDur    = 1.5
        val blockSz   = s.config.blockSize
        val sr        = s.sampleRate
        val minSz     = 2 * blockSz
        val bestSz    = math.max(minSz, (bufDur * sr).toInt)
        import numbers.Implicits._
        val bestSzHi  = bestSz.nextPowerOfTwo
        val bestSzLo  = bestSzHi >> 1
        if (bestSzHi.toDouble/bestSz < bestSz.toDouble/bestSzLo) bestSzHi else bestSzLo
      }
    }

    override protected def launch(): Unit = {
      super.launch()

      obsAural = atomic { implicit tx =>
        auralSystem.reactNow { implicit tx => {
          case AuralSystem.Running(s) => startRT(s)
          case _ =>
        }}
      }

      val bufSize = control.blockSize

      var ch = 0
      while (ch < numChannels) {
        var ci = 0
        while (ci < circleSize) {
          rtBufCircle(ci)(ch) = new Array[Float](bufSize)
          ci += 1
        }
        ch += 1
      }

      okRT = true
    }

    override protected def stopped(): Unit = {
      logStream.info(s"$this - postStop()")
      okRT = false

      if (obsAural != null) {
        atomic { implicit tx =>
          obsAural.dispose()
          obsAural = null
        }
      }

      var ch = 0
      while (ch < numChannels) {
        bufIns(ch) = null
        var ci = 0
        while (ci < circleSize) {
          rtBufCircle(ci)(ch) = null
          ci += 1
        }
        ch += 1
      }
    }

    private def canProcess: Boolean =
      pushed == numChannels && writtenCircle < readCircle

    private def process(): Unit = {
      logStream.debug(s"process() $this")
      pushed = 0

      var ch = 0
      var chunk = 0
      while (ch < numChannels) {
        val bufIn = grab(shape.inlets(ch))
        bufIns(ch)  = bufIn
        chunk       = if (ch == 0) bufIn.size else math.min(chunk, bufIn.size)
        ch += 1
      }

      val rtBuf = rtBufCircle(writtenCircle % circleSize)
      val newWrite = writtenCircle + 1
      writtenCircle = newWrite

      if (DEBUG) {
        val NOW = System.currentTimeMillis()
        val DT  = NOW - LAST_REP_IN
        if (DT > 1000) {
          val len   = rtBuf(0).length
          val thru  = ((newWrite - LAST_REP_IN_C) * len) * 1000.0 / DT
          println(s"<process()> buffers written = $writtenCircle; through-put is $thru Hz")
          LAST_REP_IN   = NOW
          LAST_REP_IN_C = newWrite
        }
      }

      ch = 0
      while (ch < numChannels) {
        var i = 0
        val a = bufIns(ch).buf
        val b = rtBuf(ch)
        val CHUNK = math.min(chunk, b.length)
        while (i < CHUNK) {
          b(i) = a(i).toFloat
          i += 1
        }
        ch += 1
      }

      ch = 0
      while (ch < numChannels) {
        bufIns(ch).release()
        ch += 1
      }

      if (shouldStop) {
        _isSuccess = true
        completeStage()
      } else {
        // println("pulling inlets")
        ch = 0
        while (ch < numChannels) {
          pull(shape.inlets(ch))
          ch += 1
        }
      }
    }
  }
}