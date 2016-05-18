package de.sciss.fscape.stream.impl

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{AmorphousShape, Attributes, Inlet, Outlet, Shape}
import de.sciss.fscape.stream.{BufD, BufI, Control}

import scala.collection.immutable
import scala.collection.immutable.{Seq => ISeq}

case class UnzipWindowShape(in0: Inlet[BufD], in1: Inlet[BufI], outlets: ISeq[Outlet[BufD]]) extends Shape {
  val inlets: ISeq[Inlet[_]] = Vector(in0, in1)

  override def deepCopy(): UnzipWindowShape =
    UnzipWindowShape(in0.carbonCopy(), in1.carbonCopy(), outlets.map(_.carbonCopy()))

  override def copyFromPorts(inlets: immutable.Seq[Inlet[_]], outlets: immutable.Seq[Outlet[_]]): Shape = {
    require(inlets .size == this.inlets .size, s"number of inlets [${inlets.size}] does not match [${this.inlets.size}]")
    require(outlets.size == this.outlets.size, s"number of outlets [${outlets.size}] does not match [${this.outlets.size}]")
    UnzipWindowShape(inlets(0).asInstanceOf[Inlet[BufD]], inlets(1).asInstanceOf[Inlet[BufI]],
      outlets.asInstanceOf[ISeq[Outlet[BufD]]])
  }
}

final class UnzipWindowStageImpl(numOutputs: Int, ctrl: Control) extends GraphStage[UnzipWindowShape] {
  val shape = UnzipWindowShape(
    in0     = Inlet[BufD]("UnzipWindow.in"),
    in1     = Inlet[BufI]("UnzipWindow.size"),
    outlets = Vector.tabulate(numOutputs)(idx => Outlet[BufD](s"UnzipWindow.out$idx"))
  )

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new UnzipWindowLogicImpl(shape, ctrl)
}

final class UnzipWindowLogicImpl(shape: UnzipWindowShape, ctrl: Control) extends GraphStageLogic(shape) {

}
