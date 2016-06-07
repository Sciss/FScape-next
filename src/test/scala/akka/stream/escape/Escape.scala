package akka.stream
package escape

import akka.stream.impl._
import akka.testkit.TestProbe

object Escape {
  def module(in: Graph[_, _]) = in.module
}

object Utils {
  /** Prints a debugging string to the console,
    * including a GraphViz DOT representation of
    * the running graph.
    */
  def debugDotGraph()(implicit mat: Materializer): Unit =
    mat match {
      case impl: ActorMaterializerImpl ⇒
        val probe = TestProbe()(impl.system)
        impl.supervisor.tell(StreamSupervisor.GetChildren, probe.ref)
        val children = probe.expectMsgType[StreamSupervisor.Children].children
        println(s"children.size = ${children.size}")
        children.foreach(_ ! StreamSupervisor.PrintDebugDump)

      case other => sys.error(s"Not an instance of ActorMaterializerImpl: $other")
    }
}
