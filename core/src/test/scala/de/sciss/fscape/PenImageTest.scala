package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.gui.SimpleGUI
import de.sciss.fscape.stream.Control

import scala.Predef.{any2stringadd => _, _}
import scala.swing.Swing

object PenImageTest {
  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    val fOut = userHome / "Documents" / "temp" /"test.png"
    fOut.parent.mkdirs()

    var gui: SimpleGUI = null

//    val g = Graph {
//      import graph._
//      val w   = 640
//      val h   = 480
//      val len = 8000
//      val x   = SinOsc(7.2/len).linLin(-1,+1,0,w-1).roundTo(1)
//      val y   = LFSaw (7.5/len).linLin(-1,+1,0,h-1).roundTo(1)
//      val sig = PenImage(
//        width  = w,
//        height = h,
//        src    = WhiteNoise(1.0).take(len),
//        x      = x,
//        y      = y,
//        //        next   = Metro(len),
//        wrap   = 0
//      )
//      val spec = ImageFile.Spec(width = w, height = h, numChannels = 1)
//      ImageFileOut(sig, fOut, spec)
//    }

    val g = Graph {
      import graph._
      // logistic function
      def fun: (GE, GE) => GE  =
        (x, r) => r * x * (1 - x)

      val ra    = 2.9
      val rb    = 4.0
      val w     = 240
      val h     = 500
      val maxIt = 200
      val y0    = 0.0
      val y1    = 1.0

      val itH   = maxIt/2
      val hm    = h - 1

      val x = ArithmSeq(0, step = 1, length = w)
      val r = ra + (rb - ra) * x / (w - 1)

      var fSel = List.empty[GE]

      var f = 0.5: GE
      for (j <- 0 until maxIt) {
        f = fun(f, r)  // recursion
        if (j > itH) {
          fSel ::= f
        }
      }

      val fSig: GE = fSel.reverse.reduce(_ ++ _)
      val y = fSig.linLin(y1, y0, 0, hm).roundTo(1)
      val sig = PenImage(
        width   = w,
        height  = h,
        src     = DC(1.0).take(itH * w),
        x       = x,
        y       = y
      )

        //  if (j > itH && x >= y0 && x <= y1) {
        //    val xs = x.linLin(y0, y1, 0, 1)
        //    wr.setSample(i, ((1 - xs) * hm).toInt, 0, 0)
        //  }

      val spec = ImageFile.Spec(width = w, height = h, numChannels = 1)
      ImageFileOut(sig, fOut, spec)
    }

    val ctl = Control()
    Swing.onEDT {
      gui = SimpleGUI(ctl)
    }

//    showStreamLog = true
    ctl.run(g)
  }
}
