package de.sciss.fscape.stream.impl

import akka.stream.Attributes
import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.stream.{Control, Leaf}

trait LeafStage {
  protected val ctrl: Control

  final def createLogic(attr: Attributes): GraphStageLogic = {
    val res = createLeaf()
//    val ls  = attr.get[Leaves].getOrElse(throw new NoSuchElementException("Missing leaves"))
//    ls.add(res)
    ctrl.addLeaf(res)
    res
  }

  protected def createLeaf(): GraphStageLogic with Leaf
}
