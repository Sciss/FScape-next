package de.sciss.fscape
package graph

import akka.stream.Outlet
import de.sciss.fscape.stream.{BufElem, Builder, OutI, StreamIn, StreamInElem, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A sorting UGen that can be thought of as a bounded priority queue.
  * It keeps all data in memory but limits the size to the
  * top `size` items. By its nature, the UGen only starts outputting
  * values once the input signal (`keys`) has finished.
  *
  * @param keys   the sorting keys; higher values mean higher priority
  * @param values the values corresponding with the keys and eventually
  *               output by the UGen. It is well possible to use the
  *               same signal both for `keys` and `values`.
  * @param size   the maximum size of the priority queue.
  */
final case class PriorityQueue(keys: GE, values: GE, size: GE) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(Vector(keys.expand, values.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(keys, values, size) = args
    mkStream[keys.A, keys.Buf, values.A, values.Buf](keys = keys, values = values, size = size.toInt)  // IntelliJ doesn't get it
  }

  private def mkStream[A, K >: Null <: BufElem[A], B, V >: Null <: BufElem[B]](keys  : StreamInElem[A, K],
                                                                               values: StreamInElem[B, V], size: OutI)
                                                                              (implicit b: Builder): StreamOut = {
    import keys  .{tpe => kTpe}  // IntelliJ doesn't get it
    import values.{tpe => vTpe}
    val out: Outlet[V] = stream.PriorityQueue[A, K, B, V](keys = keys.toElem, values = values.toElem, size = size)
    vTpe.mkStreamOut(out) // IntelliJ doesn't get it
  }
}