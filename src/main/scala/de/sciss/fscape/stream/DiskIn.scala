package de.sciss.fscape.stream

import akka.NotUsed
import akka.stream.Outlet
import akka.stream.scaladsl.{GraphDSL, Source}
import de.sciss.file._

object DiskIn {
  def apply(file: File)(implicit b: GraphDSL.Builder[NotUsed]): Outlet[Double] /* Source[Double, NotUsed] */ = {
    val source = new AudioFileSource(file)
    // b.add(Source.fromGraph(source))
    // Source.unfoldResourceAsync()
    // Source.fromGraph(source)
    // source.out
    b.add(source).out
  }
}