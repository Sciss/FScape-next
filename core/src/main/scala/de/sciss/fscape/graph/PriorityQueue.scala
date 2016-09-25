package de.sciss.fscape
package graph

import de.sciss.fscape.stream.{BufElem, Builder, OutI, StreamIn, StreamOut}

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
    mkStream[keys.A, keys.Buf, values.A, values.Buf](keys = keys, values = values, size = size.toInt)  // IntelliJ highlight bug
  }

  private def mkStream[A1, K >: Null <: BufElem[A1],
                       B , V >: Null <: BufElem[B]](keys  : StreamIn { type A = A1; type Buf = K },
                                                    values: StreamIn { type A = B ; type Buf = V }, size: OutI)
                                                   (implicit b: Builder): StreamOut = {
    val out = stream.PriorityQueue[A1, K, B, V](keys = keys.toElem, values = values.toElem, size = size)(b, keys.ordering)
    values.mkStreamOut(out)
  }
}