package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.{GE, Graph, graph, stream}
import de.sciss.numbers.Implicits._

// Note: writes a couple of PNG images to your home directory!
object RotateFlipTest extends App {
  def any2stringadd: Any = ()

  val ctrl      = stream.Control()
  val dir       = file("/") / "data" / "temp"

  val g = Graph {
    import graph._
    import RotateFlipMatrix._

    def mkDim(width: Int, height: Int): Unit = {
      val isSquare  = width == height
      val pre       = if (isSquare) "square" else if (width < height) "portrait" else "landscape"

      def mk(name: String, mode: Int): Unit = {
        val frameSize = width * height
        val dia       = ((math.min(width, height) - 1.0).squared * 2).sqrt
        val input = {
          val frame = Line(0, frameSize - 1, frameSize)
          val x     =  frame      % width
          val y     = (frame - x) / width
          val xs    = x.squared
          val ys    = y.squared
          val xm    = (-x + (width  - 1)).squared
          val ym    = (-y + (height - 1)).squared
          val red   = (xs + ys).sqrt.min(dia).linLin(0.0, dia, 1.0, 0.0)
          val green = (xm + ys).sqrt.min(dia).linLin(0.0, dia, 1.0, 0.0)
          val blue  = (xm + ym).sqrt.min(dia).linLin(0.0, dia, 1.0, 0.0)
          Seq(red, green, blue): GE
        }
        val sig   = if (name == "control") input else
          RotateFlipMatrix(input, rows = height, columns = width, mode = mode)
        val isRot = (mode & (Rot90CW| Rot90CCW)) != 0
        val spec  = ImageFile.Spec(numChannels = 3,
          width  = if (isRot) height else width,
          height = if (isRot) width  else height
        )
        ImageFileOut(file = (dir / s"test-$pre-$name.png").toURI, spec = spec, in = sig)
      }

      mk("control"    , Through         )
      mk("through"    , Through         )
      mk("flip-x"     , FlipX           )
      mk("flip-y"     , FlipY           )
      mk("rot180"     , Rot180          )
      mk("rot90cw"    , Rot90CW         )
      mk("rot90ccw"   , Rot90CCW        )
      mk("fx-r90cw"   , FlipX + Rot90CW )  // = transpose and rotate 180
      mk("fy-r90ccw"  , FlipY + Rot90CCW)  // = transpose and rotate 180
      mk("fx-r90ccw"  , FlipX + Rot90CCW)  // = transpose
      mk("fy-r90cw"   , FlipY + Rot90CW )  // = transpose
    }

//    mkDim(200, 200)
    mkDim(256, 256)
    mkDim(128, 256)
    mkDim(256, 128)
  }

  ctrl.run(g)
  println("Running.")
  import ctrl.config.executionContext
  ctrl.status.foreach { _ =>
    println("Done.")
    sys.exit()
  }
}
