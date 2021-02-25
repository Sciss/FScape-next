/*
 *  WebAudioOut.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre.graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.lucre.UGenGraphBuilder
import de.sciss.fscape.stream.{SinkIgnore, StreamIn}
import de.sciss.proc.AuralSystem

import scala.collection.immutable.{IndexedSeq => Vec}

object PhysicalOut extends ProductReader[PhysicalOut] {
  override def read(in: RefMapIn, key: String, arity: Int): PhysicalOut = {
    require (arity == 2)
    val _indices  = in.readGE()
    val _in       = in.readGE()
    new PhysicalOut(_indices, _in)
  }

  def apply(in: GE): PhysicalOut = new PhysicalOut(0, in)

  private final case class WithRef private(peer: PhysicalOut, auralSystem: AuralSystem)
    extends UGenSource.ZeroOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
      unwrap(this, peer.in.expand.outputs)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
      UGen.ZeroOut(this, inputs = args, isIndividual = true)
      ()
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
      val in = args
      lucre.stream.PhysicalOut(index = null /*indices.toInt*/, in = in.map(_.toDouble), auralSystem = auralSystem)
    }
  }
}
/** An unusual UGen that couples FScape to a real-time audio interface output.
  * It is similar to the standard (ScalaCollider) `PhysicalOut` UGen, and it was
  * added to be able to work-around limitations of SuperCollider on WebAssembly
  * (such as missing audio file input). It is also useful for testing
  * FScape processes by directly listening to signals, possibly coming
  * from real-time input as well with the corresponding `PhysicalIn` UGen.
  *
  * This UGen should be considered experimental, and it is probably not suited
  * to run in permanently a sound installation. It is important to know that the
  * implementation is different between the JVM (desktop) platform and the
  * JS (browser) platform. On the desktop, the output signal is sent to SuperCollider
  * (transferring buffer data between FScape and SuperCollider), whereas in the browser,
  * the Web Audio API is used directly. This also leads to different behaviour in terms
  * of latency and drop-out stability.
  *
  * For drop-out free combined use of `PhysicalIn` and `PhysicalOut`, consider inserting
  * a delay by prepending a `DC(0.0)` of 0.5 to 1.0 seconds duration to the output.
  *
  * @param  indices     the zero-based channel offset. '''Note:''' this is currently not
  *                     implemented, therefore leave it at the default of zero.
  * @param  in          the signal stream. The UGen has been tested
  *                     with mono and stereo signals, and bandwidth seems to be sufficient
  *                     in these cases. Higher values have not been battle-tested.
  *
  * @see [[PhysicalIn]]
  */
final case class PhysicalOut(indices: GE, in: GE) extends Lazy.Expander[Unit] {
  override protected def makeUGens(implicit b: UGenGraph.Builder): Unit = {
    val ub = UGenGraphBuilder.get(b)
    PhysicalOut.WithRef(this, ub.auralSystem)
  }
}