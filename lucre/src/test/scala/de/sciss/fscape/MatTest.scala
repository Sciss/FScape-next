// this is total crap
//package de.sciss.fscape
//
//import akka.stream.{Attributes, FlowShape, Inlet, Outlet, Shape}
//import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue}
//
//object MatTest {
//  trait Foo
//
//  class TestStage extends GraphStageWithMaterializedValue[FlowShape[Int, Int], Foo] {
//    def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Foo) = {
//      new Logic(shape) -> new Foo {}
//    }
//
//    def shape: FlowShape[Int, Int] = FlowShape(Inlet[Int]("in"), Outlet[Int]("out"))
//  }
//
//  class Logic(shape: Shape) extends GraphStageLogic(shape) {
//
//  }
//}
