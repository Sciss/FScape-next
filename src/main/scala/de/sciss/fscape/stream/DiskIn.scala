package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.Outlet
import akka.stream.scaladsl.GraphDSL
import de.sciss.file._

object DiskIn {
  def apply(file: File)(implicit b: GraphDSL.Builder[NotUsed], ctrl: Control): Outlet[BufD] = {
    val source = new AudioFileSource(file, ctrl)
    b.add(source).out
  }
}