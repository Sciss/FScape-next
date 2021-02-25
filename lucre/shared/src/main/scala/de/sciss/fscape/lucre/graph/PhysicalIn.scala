/*
 *  WebAudioIn.scala
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
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.lucre.UGenGraphBuilder
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.proc.AuralSystem

import scala.collection.immutable.{IndexedSeq => Vec}

object PhysicalIn extends ProductReader[PhysicalIn] {
  override def read(in: RefMapIn, key: String, arity: Int): PhysicalIn = {
    require (arity == 2)
    val _indices      = in.readGE()
    val _numChannels  = in.readInt()
    new PhysicalIn(_indices, _numChannels)
  }

  private final case class WithRef private(peer: PhysicalIn, auralSystem: AuralSystem)
    extends UGenSource.MultiOut {

    import peer.numChannels

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      makeUGen(Vector.empty)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
      UGen.MultiOut(this, inputs = args, numOutputs = numChannels,
        adjuncts = Adjunct.Int(numChannels) :: Nil,
        isIndividual = true, hasSideEffect = true)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Vec[StreamOut] =
      lucre.stream.PhysicalIn(index = null, numChannels = numChannels, auralSystem = auralSystem)
  }
}
/** An unusual UGen that couples FScape to a real-time audio interface input.
  * It is similar to the standard (ScalaCollider) `PhysicalIn` UGen, and it was
  * added to be able to work-around limitations of SuperCollider on WebAssembly
  * (such as missing audio file output). It is also useful for testing
  * FScape processes by directly feeding in signals, possibly playing them
  * back in real-time with the corresponding `PhysicalOut` UGen.
  *
  * This UGen should be considered experimental, and it is probably not suited
  * to run in permanently a sound installation. It is important to know that the
  * implementation is different between the JVM (desktop) platform and the
  * JS (browser) platform. On the desktop, the input signal comes from SuperCollider
  * (transferring buffer data between SuperCollider and FScape), whereas in the browser,
  * the Web Audio API is used directly. This also leads to different behaviour in terms
  * of latency and drop-out stability.
  *
  * For drop-out free combined use of `PhysicalIn` and `PhysicalOut`, consider inserting
  * a delay by prepending a `DC(0.0)` of 0.5 to 1.0 seconds duration to the output.
  *
  * @param  indices     the zero-based channel offset. '''Note:''' this is currently not
  *                     implemented, therefore leave it at the default of zero.
  * @param  numChannels the number of channels to stream. The UGen has been tested
  *                     with mono and stereo signals, and bandwidth seems to be sufficient
  *                     in these cases. Higher values have not been battle-tested.
  *
  * @see [[PhysicalOut]]
  */
final case class PhysicalIn(indices: GE = 0, numChannels: Int = 1) extends GE.Lazy {
  override protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val ub = UGenGraphBuilder.get(b)
    PhysicalIn.WithRef(this, ub.auralSystem)
  }
}