package de.sciss.proc.impl

import de.sciss.fscape.Graph

trait FScapePlatform {
  private lazy val _init: Unit = {
    Graph.addProductReaderSq({
      import de.sciss.fscape.graph._
      Seq(
        PhysicalIn,
        PhysicalOut,
      )
    })
  }

  protected def initPlatform(): Unit = _init
}
