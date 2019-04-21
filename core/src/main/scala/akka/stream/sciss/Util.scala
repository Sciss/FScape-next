package akka.stream.sciss

import akka.stream.impl.fusing.GraphInterpreter
import akka.stream.impl.fusing.GraphInterpreter.Connection
import akka.stream.stage.GraphStageLogic
import akka.stream.testkit.scaladsl.StreamTestKit
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object Util {
  /** Prints a debugging string to the console,
    * including a GraphViz DOT representation of
    * the running graph.
    */
  def debugDotGraph()(implicit mat: Materializer, executionContext: ExecutionContext): Unit =
      mat match {
        case materializer: ActorMaterializer =>
          StreamTestKit.printDebugDump(materializer.supervisor)
//      case impl: ActorMaterializer => // ActorMaterializerImpl ⇒
//        val probe = TestProbe()(materializer.system)
//        materializer.supervisor.tell(StreamSupervisor.GetChildren, probe.ref)
//        val children = probe.expectMsgType[StreamSupervisor.Children].children
//        println(s"children.size = ${children.size}")
//        children.foreach(_ ! StreamSupervisor.PrintDebugDump)

      case other => sys.error(s"Not an instance of ActorMaterializer: $other")
    }

  def portToConn(in: GraphStageLogic): Array[Connection] = in.portToConn

  private val NoFailure = Success(())

  def findFailure(in: GraphStageLogic): Try[Unit] = {
    val arr = portToConn(in)
    var i = 0
    while (i < arr.length) {
      val c = arr(i)
      c.slot match {
        case GraphInterpreter.Failed(ex, _) =>
          return Failure(ex)
        case _ =>
      }
      i += 1
    }
    NoFailure
  }
}