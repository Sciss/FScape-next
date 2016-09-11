/*
 *  ThresholdConvolution.scala
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

package de.sciss.fscape
package stream

import akka.stream.{Attributes, FanInShape5}
import de.sciss.fscape.stream.impl.{FilterIn5DImpl, FilterLogicImpl, StageImpl, NodeImpl, WindowedLogicImpl}

// XXX TODO --- what is this? I think a remainer from implementing an image processing 'smart blur'
object ThresholdConvolution {
  // (in: GE, kernel: GE, len: GE, thresh: GE = 0.0, boundary: GE = 0)
  def apply(in: OutD, kernel: OutD, size: OutI, thresh: OutD, boundary: OutI)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in      , stage.in0)
    b.connect(kernel  , stage.in1)
    b.connect(size    , stage.in2)
    b.connect(thresh  , stage.in3)
    b.connect(boundary, stage.in4)
    stage.out
  }

  private final val name = "ThresholdConvolution"

  private type Shape = FanInShape5[BufD, BufD, BufI, BufD, BufI, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape5(
      in0  = InD (s"$name.in"      ),
      in1  = InD (s"$name.kernel"  ),
      in2  = InI (s"$name.size"    ),
      in3  = InD (s"$name.thresh"  ),
      in4  = InI (s"$name.boundary"),
      out  = OutD(s"$name.out"     )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn5DImpl[BufD, BufD, BufI, BufD, BufI] {

    private[this] var winBuf: Array[Double] = _
    private[this] var size  : Int = _

    private[this] var crr : Double = _
    private[this] var cri : Double = _
    private[this] var clr : Double = _
    private[this] var cli : Double = _
    private[this] var ccr : Double = _
    private[this] var cci : Double = _
    private[this] var car : Double = _
    private[this] var cai : Double = _

    protected def startNextWindow(inOff: Int): Int = {
      val oldSize = size
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = ??? // math.max(1, bufIn1.buf(inOff))
      }
      val fullSize = size << 1
      if (size != oldSize) {
        winBuf = new Array[Double](fullSize)
      }
      if (bufIn2 != null && inOff < bufIn2.size) crr = bufIn2.buf(inOff)
      if (bufIn3 != null && inOff < bufIn3.size) cri = bufIn3.buf(inOff)
      if (bufIn4 != null && inOff < bufIn4.size) clr = bufIn4.buf(inOff)
      ???

      fullSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Int, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff, chunk)

    protected def copyWindowToOutput(readFromWinOff: Int, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff, bufOut0.buf, outOff, chunk)

    //    private var DEBUG = true

    protected def processWindow(writeToWinOff: Int): Int = {
      // println(s"crr = $crr, cri = $cri, clr = $clr, cli = $cli, ccr = $ccr, cci = $cci, car = $car, cai = $cai")

      // 'variant 1'
      // gain: 1.0/2097152
      //      val crr =  0; val cri =  0
      //      val clr = +1; val cli = +1
      //      val ccr = +1; val cci = -1
      //      val car = +1; val cai = -1

      // 'bypass'
      // gain: 1.0/4
      //      val crr = +1; val cri = +1
      //      val clr = +1; val cli = +1
      //      val ccr =  0; val cci =  0
      //      val car =  0; val cai =  0

      // 'variant 2'
      // gain: 1.0/16
      //      val crr = +1; val cri = +1
      //      val clr =  0; val cli =  0
      //      val ccr = +1; val cci = -1
      //      val car = +1; val cai = -1

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

      szC
    }
  }
}
