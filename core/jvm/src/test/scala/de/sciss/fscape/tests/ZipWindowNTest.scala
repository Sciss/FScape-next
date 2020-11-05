package de.sciss.fscape.tests

import de.sciss.file._
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph, graph}

object ZipWindowNTest extends App {
  val baseDirInt: File = file("/") / "data" / "projects" / "Imperfect"
  val coverDir  : File = baseDirInt / "catalog" / "cover"
  val pngInTemp : File = coverDir / "front" / "front-%d.jpg"
  val fOut      : File = file("/") / "data" / "temp" / "assemblage.jpg"

  val num   = 300
  val cols  = 12 // 15
  val rows  = 25 // 20
  require(cols * rows == num)
  val widthIn   = 3252
  val heightIn  = 1638
  val factor    = 2 * 3
  val scale     = 1.0 / factor
  val widthOut  = widthIn  / factor
  val heightOut = heightIn / factor
  println(s"widthOut $widthOut, heightOut $heightOut")

  if (fOut.exists() && fOut.length() > 0L) {
    println(s"File $fOut already exists. Not overwriting.")
    sys.exit()
  }

  val g = Graph {
    import graph._

    val in      = ImageFileSeqIn(pngInTemp.toURI, numChannels = 3, indices = ArithmSeq(start = 1, length = num))
    val scaled  = AffineTransform2D.scale(in, widthIn, heightIn, widthOut, heightOut,
      sx = scale, sy = scale, zeroCrossings = 3)

    val totalWidth  = widthOut  * cols
    val totalHeight = heightOut * rows
    val sizeOut     = widthOut * heightOut

    val tr  = {
      val x = Vector.tabulate(3) { ch =>
        val inC = scaled.out(ch)
//        if (ch == 0) inC.take(10).poll(1, "inC")
//        if (ch == 0) Length(inC).poll("inC.length")
        val u   = UnzipWindowN(in = inC, size = sizeOut, numOutputs = cols)
//        if (ch == 0) Length(u.out(0)).poll("u(0).length")
        val e   = u // BufferDisk(u) // .elastic(heightOut)
        val z   = ZipWindowN(in = e  , size = widthOut)
//        if (ch == 0) z.take(10).poll(0, "z")
//        if (ch == 0) Length(z).poll("z.length")
        z
      }
      x: GE
    }

    //      val tr0 = TransposeMatrix(scaled, rows    = heightOut, columns = widthOut  )
    //      val tr1 = TransposeMatrix(tr0   , columns = heightOut, rows    = totalWidth)
    //      val tr  = TransposeMatrix(tr1   , rows    = heightOut, columns = widthOut  )
    val framesOut   = totalWidth.toLong * totalHeight
    val framesOutP  = framesOut / 100.0
    (Frames(tr.out(0)) / framesOutP).ceil.poll(Metro(framesOutP), "progress")

    val specOut = ImageFile.Spec(ImageFile.Type.JPG, width = totalWidth, height = totalHeight, numChannels = 3)
    val sigOut = tr.clip(0.0, 1.0)
    ImageFileOut(file = fOut.toURI, spec = specOut, in = sigOut)
  }

  val cfg = Control.Config()
  cfg.blockSize = widthIn
  val ctrl = Control(cfg)
  ctrl.run(g)

//  Swing.onEDT {
//    SimpleGUI(ctrl)
//  }
}
