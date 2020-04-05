/*
 *  FoldCepstrum.scala
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

import akka.stream.{Attributes, FanInShape10}
import de.sciss.fscape.stream.impl.Handlers.{InDAux, InDMain, InIAux, OutDMain}
import de.sciss.fscape.stream.impl.{Handlers, StageImpl, WindowedLogicD}

object FoldCepstrum {
  def apply(in: OutD, size: OutI,
            crr: OutD, cri: OutD, clr: OutD, cli: OutD,
            ccr: OutD, cci: OutD, car: OutD, cai: OutD)(implicit b: Builder): OutD = {
    val stage0  = new Stage(b.layer)
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

  private final class Stage(layer: Layer)(implicit ctrl: Control) extends StageImpl[Shape](name) {
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

    def createLogic(attr: Attributes) = new Logic(shape, layer)
  }

  // XXX TODO -- abstract over data type (BufD vs BufI)?
  private final class Logic(shape: Shape, layer: Layer)(implicit ctrl: Control)
    extends Handlers(name, layer, shape)
      with WindowedLogicD[Shape] {

    private[this] var size: Int = _

    protected     val hIn   : InDMain   = InDMain  (this, shape.in0)
    protected     val hOut  : OutDMain  = OutDMain (this, shape.out)
    private[this] val hSize : InIAux    = InIAux   (this, shape.in1)(math.max(0, _))
    private[this] val hCRR  : InDAux    = InDAux   (this, shape.in2)()
    private[this] val hCRI  : InDAux    = InDAux   (this, shape.in3)()
    private[this] val hCLR  : InDAux    = InDAux   (this, shape.in4)()
    private[this] val hCLI  : InDAux    = InDAux   (this, shape.in5)()
    private[this] val hCCR  : InDAux    = InDAux   (this, shape.in6)()
    private[this] val hCCI  : InDAux    = InDAux   (this, shape.in7)()
    private[this] val hCAR  : InDAux    = InDAux   (this, shape.in8)()
    private[this] val hCAI  : InDAux    = InDAux   (this, shape.in9)()

    protected def tryObtainWinParams(): Boolean = {
      val ok = hSize.hasNext &&
        hCRR.hasNext &&
        hCRI.hasNext &&
        hCLR.hasNext &&
        hCLI.hasNext &&
        hCCR.hasNext &&
        hCCI.hasNext &&
        hCAR.hasNext &&
        hCAI.hasNext

      if (ok) {
        size = hSize.next()
      }
      ok
    }

    protected def winBufSize: Int = size << 1

    override protected def processWindow(): Unit = {
      val crr = hCRR.next()
      val cri = hCRI.next()
      val clr = hCLR.next()
      val cli = hCLI.next()
      val ccr = hCCR.next()
      val cci = hCCI.next()
      val car = hCAR.next()
      val cai = hCAI.next()

      val arr = winBuf
      arr(0) = arr(0) * crr
      arr(1) = arr(1) * cri

      val sz  = size
      val szC = sz << 1

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
    }
  }
}
