package de.sciss.fscape

import de.sciss.file._
import de.sciss.numbers.Implicits._

// Note: writes a couple of PNG images to your home directory!
object RotateFlipTest extends App {
  val ctrl      = stream.Control()
  val extent    = 256
  val frameSize = extent * extent
  val dia       = ((extent - 1.0).squared * 2).sqrt
  val dir       = userHome

  val g = Graph {
    import graph._

    def mk(name: String, mode: Int): Unit = {
      val input = {
        val frame = Line(0, frameSize - 1, frameSize)
        val x     =  frame      % extent
        val y     = (frame - x) / extent
        val red   = (x.squared                   + y.squared).sqrt.linLin(0.0, dia, 1.0, 0.0)
        val green = ((-x + (extent - 1)).squared + y.squared).sqrt.linLin(0.0, dia, 1.0, 0.0)
        val blue  = -red + 1.0 // - red
        Seq(red, green, blue): GE
      }
      val sig   = if (name == "control") input else
        RotateFlipMatrix(input, rows = extent, columns = extent, mode = mode)
      val spec  = ImageFile.Spec(width = extent, height = extent, numChannels = 3)
      ImageFileOut(dir / s"test-$name.png", spec, in = sig)
    }

    import RotateFlipMatrix._
    mk("control"    , Through         )
    mk("through"    , Through         )
    mk("flip-x"     , FlipX           )
    mk("flip-y"     , FlipY           )
    mk("rot180"     , Rot180          )
    mk("rot90cw"    , Rot90CW         )
    mk("rot90ccw"   , Rot90CCW        )
    mk("r90cw-fx"   , Rot90CW  + FlipX)  // = transpose
    mk("r90ccw-fy"  , Rot90CCW + FlipY)  // = transpose
    mk("r90ccw-fx"  , Rot90CCW + FlipX)  // = transpose and rotate 180
    mk("r90cw-fy"   , Rot90CW  + FlipY)  // = transpose and rotate 180
  }

  ctrl.run(g)
  println("Running.")
  import ctrl.config.executionContext
  ctrl.status.foreach { _ =>
    println("Done.")
    sys.exit()
  }
}
