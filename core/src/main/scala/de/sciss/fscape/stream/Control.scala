/*
 *  Control.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.stream.{ActorMaterializer, Materializer}
import de.sciss.file.File

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.implicitConversions
import scala.util.{Random, Success}

object Control {
  trait ConfigLike {
    /** The block size for buffers send between nodes. */
    def blockSize: Int

    /** Whether to isolate nodes through an asynchronous barrier. */
    def useAsync: Boolean

    /** The recommended maximum number of frames internally
      * stored in memory by a single node (UGen). Some UGen
      * may allow for ad-hoc disk buffering and thus can check
      * this user-defined value to decide whether to keep a buffer
      * in memory or swap it to disk.
      *
      * Note that because a frame is considered to be a `Double`,
      * the actual memory size will be eight times this value.
      */
    def nodeBufferSize: Int

    /** Random number generator seed. */
    def seed: Long

    def actorSystem: ActorSystem

    /** Whether to terminate the `actorSystem` when the rendering
      * completes. When using the default `actorSystem`, this will
      * be `true`, when setting a custom actor system, this will
      * default to `false`.
      */
    def terminateActors: Boolean

    def materializer: Materializer

    def executionContext: ExecutionContext

    def progressReporter: ProgressReporter
  }

  object Config {
    def apply() = new ConfigBuilder
    implicit def build(b: ConfigBuilder): Config = b.build
  }

  final case class Config(blockSize         : Int,
                          nodeBufferSize    : Int,
                          useAsync          : Boolean,
                          seed              : Long,
                          actorSystem       : ActorSystem,
                          materializer0     : Materializer,
                          executionContext0 : ExecutionContext,
                          progressReporter  : ProgressReporter,
                          terminateActors   : Boolean
                         )
    extends ConfigLike {

    implicit def materializer    : Materializer     = materializer0
    implicit def executionContext: ExecutionContext = executionContext0

    def toBuilder: ConfigBuilder = {
      val b = new ConfigBuilder
      b.blockSize         = blockSize
      b.nodeBufferSize    = nodeBufferSize
      b.useAsync          = useAsync
      b.seed              = seed
      b.actorSystem       = actorSystem
      b.materializer      = materializer
      b.executionContext  = executionContext
      b.progressReporter  = progressReporter
      b.terminateActors   = terminateActors
      b
    }
  }

  final class ConfigBuilder extends ConfigLike {
    /** The default block size is 1024. */
    var blockSize: Int = 1024

    /** The default internal node buffer size is 65536. */
    var nodeBufferSize: Int = 65536

    /** The default behavior is to isolate blocking nodes
      * into a separate graph. This should usually be the case.
      * It can be disabled for debugging purposes, for example
      * in order to allow the debug printer to create an entire
      * GraphViz representation.
      */
    var useAsync: Boolean = true

    private[this] var _seed = Option.empty[Long]
    private[this] lazy val defaultSeed: Long = System.currentTimeMillis()

    def seed: Long = _seed.getOrElse {
      _seed = Some(defaultSeed)
      defaultSeed
    }

    def seed_=(value: Long): Unit = _seed = Some(value)

    private[this] var _actor: ActorSystem = _
    private[this] var _actorSet           = false
    private[this] lazy val defaultActor: ActorSystem = ActorSystem("fscape")

    def actorSystem: ActorSystem = if (_actorSet) _actor else defaultActor

    def actorSystem_=(value: ActorSystem): Unit = {
      _actor    = value
      _actorSet = true
    }

    private[this] var _terminateActors: Boolean = _
    private[this] var _terminateSet             = false

    def terminateActors: Boolean = if (_terminateSet) _terminateActors else !_actorSet

    def terminateActors_=(value: Boolean): Unit = {
      _terminateActors  = value
      _terminateSet     = true
    }

    private[this] var _mat: Materializer = _
    private[this] lazy val defaultMat: Materializer = ActorMaterializer()(actorSystem)

    def materializer: Materializer = {
      if (_mat == null) _mat = defaultMat
      _mat
    }
    def materializer_=(value: Materializer): Unit =
      _mat = value

    private[this] var _exec: ExecutionContext = _

    def executionContext: ExecutionContext = {
      if (_exec == null) _exec = ExecutionContext.Implicits.global
      _exec
    }
    def executionContext_=(value: ExecutionContext): Unit =
      _exec = value

    var progressReporter: ProgressReporter = NoReport

    def build = Config(
      blockSize         = blockSize,
      nodeBufferSize    = nodeBufferSize,
      useAsync          = useAsync,
      seed              = seed,
      actorSystem       = actorSystem,
      terminateActors   = terminateActors,
      materializer0     = materializer,
      executionContext0 = executionContext,
      progressReporter  = progressReporter
    )
  }

  def apply(config: Config = Config()): Control = new Impl(config)

  implicit def fromBuilder(implicit b: Builder): Control = b.control

//  final case class ProgressPart(label: String, frac: Double)

  final case class ProgressReport(total: Double, partLabel: String, part: Double)

  type ProgressReporter = ProgressReport => Unit

  final val NoReport: ProgressReporter = { _ => () }

  final case class Stats(numBufD: Int, numBufI: Int, numBufL: Int, numNodes: Int) {
    override def toString =
      s"$productPrefix(numBufD = $numBufD, numBufI = $numBufI, numBufL = $numBufL, numNodes = $numNodes)"
  }

  // ------------------------

  private final class Impl(val config: Config) extends AbstractImpl {
    protected def expand(graph: Graph): UGenGraph = UGenGraph.build(graph)(this)
  }

  private[fscape] trait AbstractImpl extends Control {
    // ---- abstract ----

    protected def expand(graph: Graph): UGenGraph

    // ---- impl ----

    override def toString = s"Control@${hashCode().toHexString}"

    final def blockSize     : Int = config.blockSize
    final def nodeBufferSize: Int = config.nodeBufferSize

    private[this] val queueD      = new ConcurrentLinkedQueue[BufD]
    private[this] val queueI      = new ConcurrentLinkedQueue[BufI]
    private[this] val queueL      = new ConcurrentLinkedQueue[BufL]
    private[this] val nodes       = mutable.Set.empty[Node]
    private[this] val sync        = new AnyRef
    private[this] val statusP     = Promise[Unit]()
    private[this] var _actor      = null : ActorRef
    private[this] val metaRand    = new Random(config.seed)
    private[this] var _progLabels = Array.empty[String]
    private[this] var _progParts  = Array.empty[Double]
    private[this] val _progHasRep = config.progressReporter ne NoReport

    final def debugDotGraph(): Unit = {
      akka.stream.sciss.Util.debugDotGraph()(config.materializer, config.executionContext)
    }

    final def run(graph: Graph): UGenGraph = {
      val ugens = expand(graph)
      runExpanded(ugens)
      ugens
    }

    private def actSetProgress(key: Int, frac: Double): Unit = {
      val pf    = _progParts
      val clip  = if (frac < 0.0) 0.0 else if (frac > 1.0) 1.0 else frac
      if (pf(key) != clip) {
        pf(key)     = clip
        if (_progHasRep) {
          var i     = 0
          var total = 0.0
          while (i < pf.length) {
            total += pf(i)
            i += 1
          }
          if (total > 1.0) total = 1.0
          val report = ProgressReport(total = total, partLabel = _progLabels(key), part = clip)
          config.progressReporter(report)
        }
      }
    }

    private def actRemoveNode(n: Node, context: ActorContext, self: ActorRef): Unit = {
      if (nodes.remove(n)) {
        // XXX TODO --- check for error other than `Cancelled` --- how?
        if (nodes.isEmpty) {
          logControl(s"${hashCode().toHexString} actRemoveNode complete")
          /* val ok = */ statusP.tryComplete(Success(()))
          // if (!ok) logControl(s"${hashCode().toHexString} promise already completed")
          sync.synchronized {
            context.stop(self)
            _actor = null
          }
          if (config.terminateActors) config.actorSystem.terminate()
        }
      } else {
        Console.err.println(s"Warning: node $n was not registered with Control")
      }
    }

    // We kind of emulate what Akka Stream would do itself
    // if we had used `preStart`: we have to buffer all polls
    // until the initialization of all nodes is done. Here
    // we do that by first going through all `NodeHasInit`
    // and then mapping the resulting future to launch the
    // nodes (`launch` will typically poll a node's inputs).
    private def actLaunch(): Unit = {
      logControl(s"${hashCode().toHexString} actLaunch")
      val futInit: Seq[Future[Unit]] = nodes.iterator.collect {
        case ni: NodeHasInit => ni.initAsync()
      } .toSeq

      import config.executionContext
      Future.sequence(futInit).foreach { _ =>
        nodes.foreach { n =>
          n.launchAsync()
        }
      }
    }

    private def actCancel(): Unit = {
      logControl(s"${hashCode().toHexString} actCancel")
      val ex = Cancelled()
      statusP.tryFailure(ex)
      nodes.foreach { n =>
        n.failAsync(ex)
      }
    }

    private final class ActorImpl extends Actor {
      def receive: Receive = {
        case SetProgress(key, frac) => actSetProgress(key, frac)
        case RemoveNode(n)          => actRemoveNode(n, context, self)
        case Launch                 => actLaunch()
        case Cancel                 => actCancel()
      }
    }

    private def mkActor(): Unit = sync.synchronized {
      require(_actor == null)
      _actor = config.actorSystem.actorOf(Props(new ActorImpl))
    }

    final def runExpanded(ugens: UGenGraph): Unit = {
      val r = ugens.runnable
      logControl(s"${hashCode().toHexString} runExpanded")
      mkActor()
      r.run()(config.materializer)
      _actor ! Launch
    }

    final def mkRandom(): Random = /* sync.synchronized */ {
      new Random(metaRand.nextLong())
    }

    final def mkProgress(label: String): Int = /* sync.synchronized */ {
      val res = _progLabels.length
      _progLabels :+= label
      _progParts  :+= 0.0
      res
    }

    final def setProgress(key: Int, frac: Double): Unit =
      if (!frac.isNaN) _actor ! SetProgress(key = key, frac = frac)

    final def borrowBufD(): BufD = {
      val res0 = queueD.poll()
      val res  = if (res0 == null) BufD.alloc(blockSize) else {
        res0.acquire()
        res0.size = res0.buf.length
        res0
      }
      // println(s"Control.borrowBufD(): $res / ${res.allocCount()}")
      // assert(res.allocCount() == 1, res.allocCount())
      res
    }

    final def returnBufD(buf: BufD): Unit = {
      require(buf.allocCount() == 0)
      // println(s"control: ${buf.hashCode.toHexString} - ${buf.buf.toVector.hashCode.toHexString}")
      queueD.offer(buf) // XXX TODO -- limit size?
    }

    final def borrowBufI(): BufI = {
      val res0 = queueI.poll()
      if (res0 == null) BufI.alloc(blockSize) else {
        res0.acquire()
        res0.size = res0.buf.length
        res0
      }
    }

    final def returnBufI(buf: BufI): Unit = {
      require(buf.allocCount() == 0)
      queueI.offer(buf) // XXX TODO -- limit size?
    }

    final def borrowBufL(): BufL = {
      val res0 = queueL.poll()
      if (res0 == null) BufL.alloc(blockSize) else {
        res0.acquire()
        res0.size = res0.buf.length
        res0
      }
    }

    final def returnBufL(buf: BufL): Unit = {
      require(buf.allocCount() == 0)
      queueL.offer(buf) // XXX TODO -- limit size?
    }

    // called during materialization, no sync needed
    final private[stream] def addNode   (n: Node): Unit = nodes += n
    // called during run, have to relay using actor
    final private[stream] def removeNode(n: Node): Unit = _actor ! RemoveNode(n)

    final def status: Future[Unit] = statusP.future

    final def cancel(): Unit = sync.synchronized {
      if (_actor != null) _actor ! Cancel
    }

    // XXX TODO --- should be in the actor body
    final def stats: Stats = {
      val res = Stats(numBufD = queueD.size(), numBufI = queueI.size(), numBufL = queueL.size(),
        numNodes = nodes.size)
      println("--- NODES: ---")
      nodes.foreach { n =>
        println(n)
//        val gs   = n.asInstanceOf[GraphStageLogic]
//        val conn = akka.stream.sciss.Util.portToConn(gs)
//        val ni   = n.asInstanceOf[NodeImpl[Shape]]
//        val ins  = ni.shape.inlets
//        val numIns = ins.size
//        val outs = ni.shape.outlets
//        val numOuts = outs.size
//        println("  ins:")
//        (ins zip conn.take(numIns)).foreach { case (in, con) =>
//          println(s"   $in - $con")
//        }
//        println("  outs:")
//        (outs zip conn.takeRight(numOuts)).foreach { case (out, con) =>
//            println(s"   $out - $con")
//        }
      }
      res
    }

    final def createTempFile(): File = File.createTemp()
  }

  // ---- actor messages

  private final case class RemoveNode(node: Node)
  private case object      Launch
  private case object      Cancel
  private final case class SetProgress(key: Int, frac: Double)
}
trait Control {
  /** Global buffer size. The guaranteed size of the double and integer arrays.
    * A shortcut for `config.bufSize`.
    */
  def blockSize: Int

  /** A shortcut for `config.nodeBufferSize`.
    */
  def nodeBufferSize: Int

  /** Borrows a double buffer. Its size is reset to `bufSize`. */
  def borrowBufD(): BufD

  /** Borrows an integer buffer. Its size is reset to `bufSize`. */
  def borrowBufI(): BufI

  /** Borrows a long buffer. Its size is reset to `bufSize`. */
  def borrowBufL(): BufL

  /** Returns a double buffer. When `buf.borrowed` is `false`, this is a no-op.
    * This should never be called directly but only by the buffer itself
    * through `buf.release()`.
    */
  def returnBufD(buf: BufD): Unit

  /** Returns an integer buffer. When `buf.borrowed` is `false`, this is a no-op.
    * This should never be called directly but only by the buffer itself
    * through `buf.release()`.
    */
  def returnBufI(buf: BufI): Unit

  /** Returns an integer buffer. When `buf.borrowed` is `false`, this is a no-op.
    * This should never be called directly but only by the buffer itself
    * through `buf.release()`.
    */
  def returnBufL(buf: BufL): Unit

  /** Adds a node of a stage logic. Must be called during materialization. */
  private[stream] def addNode(n: Node): Unit

  /** Removes a finished node of a stage logic. */
  private[stream] def removeNode(n: Node): Unit

  /** Registers a progress reporter. */
  private[stream] def mkProgress(label: String): Int

  /** Reports the progress for a particular instance. */
  private[stream] def setProgress(key: Int, frac: Double): Unit

  /** Creates a temporary file. The caller is responsible for deleting the file
    * after it is not needed any longer. (The file will still be marked `deleteOnExit`)
    */
  def createTempFile(): File

  /** Cancels the process. This works by cancelling all registered leaves. If the graph
    * is correctly constructed, this should shut down all connected trees from there automatically.
    */
  def cancel(): Unit

  /** Creates an aggregated `Future` over the state of the graph.
    * In the case of cancelling the graph, the result will be `Failure(Cancelled())`.
    */
  def status: Future[Unit]

  def stats : Control.Stats

  val config: Control.Config

  def debugDotGraph(): Unit

  /** Expands with default builder and then runs the graph. */
  def run(graph: Graph): UGenGraph

  /** Runs an already expanded graph. */
  def runExpanded(ugens: UGenGraph): Unit

  def mkRandom(): Random
}