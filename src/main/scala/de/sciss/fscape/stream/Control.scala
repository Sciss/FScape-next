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

package de.sciss.fscape.stream

import java.util.concurrent.ConcurrentLinkedQueue

import de.sciss.file.File

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

object Control {
  def apply(bufSize: Int)(implicit exec: ExecutionContext): Control = new Impl(bufSize)

  implicit def fromBuilder(implicit b: Builder): Control = b.control

  private final class Impl(val bufSize: Int)(implicit exec: ExecutionContext) extends Control {
    override def toString = s"Control@${hashCode().toHexString}"

    private[this] val queueD  = new ConcurrentLinkedQueue[BufD]
    private[this] val queueI  = new ConcurrentLinkedQueue[BufI]
    private[this] var leaves  = List.empty[Leaf]

    def borrowBufD(): BufD = {
      val res0 = queueD.poll()
      val res  = if (res0 == null) BufD.alloc(bufSize) else {
        res0.acquire()
        res0
      }
      // println(s"Control.borrowBufD(): $res / ${res.allocCount()}")
      // assert(res.allocCount() == 1, res.allocCount())
      res
    }

    def returnBufD(buf: BufD): Unit = {
      require(buf.allocCount() == 0)
      queueD.offer(buf) // XXX TODO -- limit size?
    }

    def borrowBufI(): BufI = {
      val res0 = queueI.poll()
      if (res0 == null) BufI.alloc(bufSize) else {
        res0.acquire()
        res0
      }
    }

    def returnBufI(buf: BufI): Unit = {
      require(buf.allocCount() == 0)
      queueI.offer(buf) // XXX TODO -- limit size?
    }

    def addLeaf(l: Leaf): Unit = leaves ::= l

    def status: Future[Unit] = {
      val seq = leaves.map(_.result)
      Future.fold[Any, Unit](seq)(())((_, _) => ())  // is there a simpler way to write this?
    }

    def cancel(): Unit = leaves.foreach(_.cancel())

    def stats = Stats(numBufD = queueD.size(), numBufI = queueI.size())

    def createTempFile(): File = File.createTemp()
  }

  final case class Stats(numBufD: Int, numBufI: Int)
}
trait Control {
  /** Global buffer size. The guaranteed size of the double and integer arrays. */
  def bufSize: Int

  /** Borrows a double buffer. Its size is reset to `bufSize`. */
  def borrowBufD(): BufD

  /** Borrows an integer buffer. Its size is reset to `bufSize`. */
  def borrowBufI(): BufI

  /** Returns a double buffer. When `buf.borrowed` is `false`, this is a no-op. */
  def returnBufD(buf: BufD): Unit

  /** Returns an integer buffer. When `buf.borrowed` is `false`, this is a no-op. */
  def returnBufI(buf: BufI): Unit

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
}