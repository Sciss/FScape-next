/*
 *  FoldCepstrum.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import de.sciss.fscape.Util
import de.sciss.fscape.stream.impl.{FilterIn2Impl, WindowedLogicImpl}

object FoldCepstrum {
  def apply(in: Outlet[BufD], size: Outlet[BufI])
           (implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    import GraphDSL.Implicits._
    in   ~> stage.in0
    size ~> stage.in1

    stage.out
  }

  private final class Stage(implicit ctrl: Control)
    extends GraphStage[FanInShape2[BufD, BufI, BufD]] {

    val shape = new FanInShape2(
      in0 = Inlet [BufD]("FoldCepstrum.in"  ),
      in1 = Inlet [BufI]("FoldCepstrum.size"),
      out = Outlet[BufD]("FoldCepstrum.out" )
    )

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(protected val shape: FanInShape2[BufD, BufI, BufD])
                           (implicit protected val ctrl: Control)
    extends GraphStageLogic(shape)
      with WindowedLogicImpl[BufD, BufD, FanInShape2[BufD, BufI, BufD]]
      with FilterIn2Impl                            [BufD, BufI, BufD] {

    protected val in0: Inlet[BufD] = shape.in0

    protected def allocOutBuf(): BufD = ctrl.borrowBufD()

    private[this] var winBuf      : Array[Double] = _
    private[this] var size        : Int = _

    protected def startNextWindow(inOff: Int): Int = {
      val oldSize = size
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = math.max(1, bufIn1.buf(inOff))
      }
      val fullSize = size << 1
      if (size != oldSize) {
        winBuf = new Array[Double](fullSize)
      }
      fullSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff, bufOut.buf, outOff, chunk)

//    private var DEBUG = true

    protected def processWindow(writeToWinOff: Int): Int = {
      // 'variant 1'
      //    val crr =  0; val cri =  0
      //    val clr = +1; val cli = +1
      //    val ccr = +1; val cci = -1
      //    val car = +1; val cai = -1

      // 'bypass'
      //    val crr = +1; val cri = +1
      //    val clr = +1; val cli = +1
      //    val ccr =  0; val cci =  0
      //    val car =  0; val cai =  0

      // 'variant 2'
      val crr = +1; val cri = +1
      val clr =  0; val cli =  0
      val ccr = +1; val cci = -1
      val car = +1; val cai = -1

//      if (DEBUG) {
//        import de.sciss.file._
//        import de.sciss.synth.io._
//        val afBefore = AudioFile.openWrite(userHome/"Music"/"work"/"_NEXT_BEFORE.aif",
//          AudioFileSpec(numChannels = 1, sampleRate = 44100.0))
//        val afBuf = Array(winBuf.map(_.toFloat))
//        afBefore.write(afBuf)
//        afBefore.close()
//      }

      val arr = winBuf
      arr(0) = arr(0) * crr
      arr(1) = arr(1) * cri

      val sz  = size
      val szC = sz << 1
      if (writeToWinOff < szC) Util.clear(arr, writeToWinOff, szC - writeToWinOff)

      var i = 2
      var j = szC - 2
      while (i < sz) {
        val reL = arr(i)
        val reR = arr(j)
        arr(i) = crr * reL + ccr * reR
        arr(j) = clr * reR + car * reL
        val imL = arr(i + 1)
        val imR = arr(j + 1)
        arr(i + 1) = cri * imL + cci * imR
        arr(j + 1) = cli * imR + cai * imL
        i += 2
        j -= 2
      }
      arr(i) = arr(i) * (ccr + clr)
      i += 1
      arr(i) = arr(i) * (cci + cli)
      // i += 1

//      if (DEBUG) {
//        import de.sciss.file._
//        import de.sciss.synth.io._
//        val afAfter = AudioFile.openWrite(userHome/"Music"/"work"/"_NEXT_AFTER.aif",
//          AudioFileSpec(numChannels = 1, sampleRate = 44100.0))
//        val afBuf = Array(winBuf.map(_.toFloat))
//        afAfter.write(afBuf)
//        afAfter.close()
//
//        DEBUG = false
//      }

      szC
    }
  }
}
