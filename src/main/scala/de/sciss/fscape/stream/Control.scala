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

import scala.concurrent.{ExecutionContext, Future}

object Control {
  def apply(bufSize: Int)(implicit exec: ExecutionContext): Control = new Impl(bufSize)

  private final class Impl(val bufSize: Int)(implicit exec: ExecutionContext) extends Control {
    override def toString = s"Control@${hashCode().toHexString}"

    private[this] val queueD  = new ConcurrentLinkedQueue[BufD]
    private[this] val queueI  = new ConcurrentLinkedQueue[BufI]
    private[this] var leaves  = List.empty[Leaf]

    def borrowBufD(): BufD = {
      val res0 = queueD.poll()
      if (res0 == null) new BufD(new Array[Double](bufSize), size = bufSize, borrowed = true) else res0
    }

    def returnBufD(buf: BufD): Unit =
      if (buf.borrowed) queueD.offer(buf) // XXX TODO -- limit size?

    def borrowBufI(): BufI = {
      val res0 = queueI.poll()
      if (res0 == null) new BufI(new Array[Int](bufSize), size = bufSize, borrowed = true) else res0
    }

    def returnBufI(buf: BufI): Unit =
      if (buf.borrowed) queueI.offer(buf) // XXX TODO -- limit size?

    def addLeaf(l: Leaf): Unit = leaves ::= l

    def status: Future[Unit] = {
      val seq = leaves.map(_.result)
      Future.fold[Any, Unit](seq)(())((_, _) => ())  // is there a simpler way to write this?
    }

    def cancel(): Unit = leaves.foreach(_.cancel())

    def stats = Stats(numBufD = queueD.size(), numBufI = queueI.size())
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