package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.Outlet
import akka.stream.scaladsl.{GraphDSL, Source}
import de.sciss.file._

object DiskIn {
  def apply(file: File)(implicit b: GraphDSL.Builder[NotUsed]): Source[Double, NotUsed] = {
    val source = new AudioFileSource(file)
    b.add(source)
    Source.fromGraph(source)
  }
}