/*
 *  DebugThrough.scala
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
package graph

import de.sciss.fscape.UGen.Aux
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

object DebugThrough {
  implicit final class DebugThroughOp(private val in: GE) extends AnyVal {
    def <| (label: String): GE = DebugThrough(in, label)
  }
}
/** A UGen that passes through its input, and upon termination prints
  * the number of frames seen.
  *
  * For convenience, `import DebugThrough._` allows to write
  *
  * {{{
  *   val sin = SinOsc(freq) <| "my-sin"
  * }}}
  *
  * @param in     the input to be pulled.
  * @param label  an identifying label to prepend to the printing.
  */
final case class DebugThrough(in: GE, label: String) extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(in.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, inputs = args, aux = Aux.String(label) :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(in) = args
    import in.tpe
    val out = stream.DebugThrough[in.A, in.Buf](in = in.toElem, label = label)
    tpe.mkStreamOut(out)
  }
}