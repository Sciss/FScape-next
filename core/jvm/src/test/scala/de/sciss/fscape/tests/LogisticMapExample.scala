package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph, graph}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

// writes to `~/Documents/temp/log-map-<n>.png`
object LogisticMapExample {
  def any2stringadd: Any = ()

  def main(args: Array[String]): Unit = {
    val c4 = Config(id = 4,
      ra = 2.4, rb = 3.57, w = 1024, h = 768, maxIt = 100,
      fun = bentFun())

    val c5 = Config(id = 5,
      ra = 3.57, rb = 3.78, w = 1024, h = 768, maxIt = 100,
      fun = bentFun())

    val c6 = Config(id = 6,
      ra = 3.57, rb = 3.78, w = 1024, h = 768, maxIt = 400,
      fun = bentFun())

    val c7 = Config(id = 7,
      ra = 3.57, rb = 3.59, w = 1024, h = 768, maxIt = 400,
      y0 = 0.32, y1 = 0.35,
      fun = bentFun())

    val cc = Seq(c4, c5, c6, c7)

    cc.foreach(run)
  }

  // logistic function
  def stdFun: (GE, GE) => GE =
    (x, r) => r * x * (-x + 1.0)

  def bentFun(p: GE = 0.96): (GE, GE) => GE =
    (x, r) => (r * x * (1.0 - x)).pow(p)

  case class Config(id: Int = 0, ra: Double = 2.9, rb: Double = 4.0, w: Int = 240, h: Int = 500,
                    maxIt:Int = 200, y0: Double = 0.0, y1: Double = 1.0,
                    fun: (GE, GE) => GE = stdFun, invert: Boolean = false) {
    require (maxIt % 2 == 0, s"maxIt ($maxIt) must be even")
  }

  def run(config: Config): Unit = {
    import config._
    val fOut = userHome / "Documents" / "temp" / s"log-map-$id.png"
    fOut.parent.mkdirs()

    val gr = Graph {
      import graph._

      val itH = maxIt/2
      val wm  = w - 1
      val hm  = h - 1

      val x0  = ArithmSeq(0, length = w)  // x coordinates
      val r   = x0.linLin(0, wm, ra, rb)  // ... become `r` in the logistic function

      // the `fold` operation on a sequence can be used to
      // apply a function recursively. We thread two values,
      // `f` and `g`. `f` is the recursive variable (or `x` in the formula),
      // beginning at 0.5. `g` is accumulating the results of the
      // recursion, keeping only the second half of all iterations.

      val (_, fSeq) = (0 until maxIt).foldLeft[(GE, GE)]((0.5, Empty())) {
        case ((f, g), it) =>
          val fN = fun(f, r)                    // recursion
          val gN = if (it < itH) g else g ++ fN // collect second half of iterations
          (fN, gN)
        }

      val x     = RepeatWindow(x0, size = w, num = itH)
      val y     = fSeq.linLin(y1, y0, 0, hm).roundTo(1)

      val cFg   = if (invert) 1.0 else 0.0  // foreground "color"
      val cBg   = 1.0 - cFg                 // background "color"

      // apply the pixels
      val sig = PenImage(
        width   = w,
        height  = h,
        src     = DC(cFg).take(itH * w),
        dst     = cBg,
        x       = x,
        y       = y
      )

      val spec = ImageFile.Spec(width = w, height = h, numChannels = 1)
      ImageFileOut(sig, fOut.toURI, spec)
    }

    val ctl = Control()
    ctl.run(gr)
    println(s"Rendering $id...")
    Await.result(ctl.status, Duration.Inf)
  }
}
