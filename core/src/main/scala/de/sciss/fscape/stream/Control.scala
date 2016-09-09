/*
 *  Control.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package stream

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import de.sciss.file.File

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Random

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

    def materializer: Materializer

    def executionContext: ExecutionContext
  }
  object Config {
    def apply() = new ConfigBuilder
    implicit def build(b: ConfigBuilder): Config = b.build
  }

  final case class Config(blockSize: Int, nodeBufferSize: Int,
                          useAsync: Boolean, seed: Long, materializer0: Materializer,
                          executionContext0: ExecutionContext)
    extends ConfigLike {

    implicit def materializer    : Materializer     = materializer0
    implicit def executionContext: ExecutionContext = executionContext0
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

    private[this] var _mat: Materializer = _
    private[this] lazy val defaultMat: Materializer = ActorMaterializer()(ActorSystem())

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

    def build = Config(
      blockSize         = blockSize,
      nodeBufferSize    = nodeBufferSize,
      useAsync          = useAsync,
      seed              = seed,
      materializer0     = materializer,
      executionContext0 = executionContext
    )
  }

  def apply(config: Config = Config()): Control = new Impl(config)

  implicit def fromBuilder(implicit b: Builder): Control = b.control

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

    private[this] val queueD  = new ConcurrentLinkedQueue[BufD]
    private[this] val queueI  = new ConcurrentLinkedQueue[BufI]
    private[this] val queueL  = new ConcurrentLinkedQueue[BufL]
    private[this] var leaves  = List.empty[Leaf]
    private[this] val sync    = new AnyRef

    final def debugDotGraph(): Unit = akka.stream.sciss.Util.debugDotGraph()(config.materializer)

    final def run(graph: Graph): UGenGraph = {
      val ugens = expand(graph)
      ugens.runnable.run()(config.materializer)
      ugens
    }

    private[this] val metaRand = new Random(config.seed)

    final def mkRandom(): Random = sync.synchronized(new Random(metaRand.nextLong()))

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

    final def addLeaf(l: Leaf): Unit = sync.synchronized(leaves ::= l)

    final def status: Future[Unit] = {
      import config.executionContext
      val seq = leaves.map(_.result)
      Future.fold[Any, Unit](seq)(())((_, _) => ())  // is there a simpler way to write this?
    }

    final def cancel(): Unit = leaves.foreach(_.cancel())

    final def stats = Stats(numBufD = queueD.size(), numBufI = queueI.size())

    final def createTempFile(): File = File.createTemp()
  }

  final case class Stats(numBufD: Int, numBufI: Int)
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

  /** Adds a leaf node that can be cancelled. Must be called during materialization. */
  def addLeaf(l: Leaf): Unit

  /** Creates a temporary file. The caller is responsible to deleting the file
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

  def stats: Control.Stats

  val config: Control.Config

  def debugDotGraph(): Unit

  def run(graph: Graph): UGenGraph

  def mkRandom(): Random
}