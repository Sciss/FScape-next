package akka.stream.sciss

import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.testkit.scaladsl.StreamTestKit

import scala.concurrent.ExecutionContext

object Util {
  /** Prints a debugging string to the console,
    * including a GraphViz DOT representation of
    * the running graph.
    */
  def debugDotGraph()(implicit mat: Materializer, executionContext: ExecutionContext): Unit =
      mat match {
        case materializer: ActorMaterializer => StreamTestKit.printDebugDump(materializer.supervisor)
//      case impl: ActorMaterializer => // ActorMaterializerImpl ⇒
//        val probe = TestProbe()(impl.system)
//        impl.supervisor.tell(StreamSupervisor.GetChildren, probe.ref)
//        val children = probe.expectMsgType[StreamSupervisor.Children].children
//        println(s"children.size = ${children.size}")
//        children.foreach(_ ! StreamSupervisor.PrintDebugDump)

      case other => sys.error(s"Not an instance of ActorMaterializerImpl: $other")
    }

//  def portToConn(in: GraphStageLogic): Array[Connection] = in.portToConn
}