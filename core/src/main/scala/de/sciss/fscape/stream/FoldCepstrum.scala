/*
 *  FoldCepstrum.scala
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

import akka.stream.{Attributes, FanInShape10}
import de.sciss.fscape.stream.impl.{FilterIn10DImpl, FilterLogicImpl, StageImpl, NodeImpl, WindowedLogicImpl}

object FoldCepstrum {
  def apply(in: OutD, size: OutI,
            crr: OutD, cri: OutD, clr: OutD, cli: OutD,
            ccr: OutD, cci: OutD, car: OutD, cai: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage
    val stage   = b.add(stage0)
    b.connect(in   , stage.in0)
    b.connect(size , stage.in1)
    b.connect(crr  , stage.in2)
    b.connect(cri  , stage.in3)
    b.connect(clr  , stage.in4)
    b.connect(cli  , stage.in5)
    b.connect(ccr  , stage.in6)
    b.connect(cci  , stage.in7)
    b.connect(car  , stage.in8)
    b.connect(cai  , stage.in9)
    stage.out
  }

  private final val name = "FoldCepstrum"

  private type Shape = FanInShape10[BufD, BufI, BufD, BufD, BufD, BufD, BufD, BufD, BufD, BufD, BufD]

  private final class Stage(implicit ctrl: Control) extends StageImpl[Shape](name) {
    val shape = new FanInShape10(
      in0  = InD (s"$name.in"  ),
      in1  = InI (s"$name.size"),
      in2  = InD (s"$name.crr" ),
      in3  = InD (s"$name.cri" ),
      in4  = InD (s"$name.clr" ),
      in5  = InD (s"$name.cli" ),
      in6  = InD (s"$name.ccr" ),
      in7  = InD (s"$name.cci" ),
      in8  = InD (s"$name.car" ),
      in9  = InD (s"$name.cai" ),
      out  = OutD(s"$name.out" )
    )

    def createLogic(attr: Attributes) = new Logic(shape)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape)(implicit ctrl: Control)
    extends NodeImpl(name, shape)
      with WindowedLogicImpl[Shape]
      with FilterLogicImpl[BufD, Shape]
      with FilterIn10DImpl[BufD, BufI, BufD, BufD, BufD, BufD, BufD, BufD, BufD, BufD] {

    private[this] var winBuf      : Array[Double] = _
    private[this] var size        : Int = _

    private[this] var crr : Double = _
    private[this] var cri : Double = _
    private[this] var clr : Double = _
    private[this] var cli : Double = _
    private[this] var ccr : Double = _
    private[this] var cci : Double = _
    private[this] var car : Double = _
    private[this] var cai : Double = _

    protected def startNextWindow(inOff: Int): Long = {
      val oldSize = size
      if (bufIn1 != null && inOff < bufIn1.size) {
        size = math.max(1, bufIn1.buf(inOff))
      }
      val fullSize = size << 1
      if (size != oldSize) {
        winBuf = new Array[Double](fullSize)
      }
      if (bufIn2 != null && inOff < bufIn2.size) crr = bufIn2.buf(inOff)
      if (bufIn3 != null && inOff < bufIn3.size) cri = bufIn3.buf(inOff)
      if (bufIn4 != null && inOff < bufIn4.size) clr = bufIn4.buf(inOff)
      if (bufIn5 != null && inOff < bufIn5.size) cli = bufIn5.buf(inOff)
      if (bufIn6 != null && inOff < bufIn6.size) ccr = bufIn6.buf(inOff)
      if (bufIn7 != null && inOff < bufIn7.size) cci = bufIn7.buf(inOff)
      if (bufIn8 != null && inOff < bufIn8.size) car = bufIn8.buf(inOff)
      if (bufIn9 != null && inOff < bufIn9.size) cai = bufIn9.buf(inOff)
      
      fullSize
    }

    protected def copyInputToWindow(inOff: Int, writeToWinOff: Long, chunk: Int): Unit =
      Util.copy(bufIn0.buf, inOff, winBuf, writeToWinOff.toInt, chunk)

    protected def copyWindowToOutput(readFromWinOff: Long, outOff: Int, chunk: Int): Unit =
      Util.copy(winBuf, readFromWinOff.toInt, bufOut0.buf, outOff, chunk)

    protected def processWindow(writeToWinOff: Long): Long = {
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
      if (writeToWinOff < szC) {
        val writeOffI = writeToWinOff.toInt
        Util.clear(arr, writeOffI, szC - writeOffI)
      }

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
