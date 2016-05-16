package de.sciss.fscape.stream

import scala.concurrent.Future

trait Leaf {
  def result: Future[Any]

  def cancel(): Unit
}
