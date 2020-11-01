/*
 *  ProgressFrames.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

/** A variant of progress UGen for the common case where one wants
  * to count the incoming frames of a signal.
  *
  * It is possible to instantiate multiple instances of this UGen,
  * in which cases their individual progress reports will simply
  * be added up (and clipped to the range from zero to one).
  *
  * The progress update is automatically triggered, using a combination
  * where both the progress fraction (0.2%) and elapsed time (100ms)
  * must have increased.
  *
  * @param in         signal whose length to monitor
  * @param numFrames  the expected length of the input signal
  * @param label      the label can be used to distinguish the
  *                   contributions of different progress UGens
  */
final case class ProgressFrames(in: GE, numFrames: GE, label: String = "render")
  extends UGenSource.ZeroOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, numFrames.expand +: in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
    val trunc = args.take(2)  // if the input was multi-channel, just use the first channel
    UGen.ZeroOut(this, inputs = trunc, adjuncts = Adjunct.String(label) :: Nil)
    ()
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val Vec(numFrames, in) = args
    stream.ProgressFrames(in = in.toAny, numFrames = numFrames.toLong, label = label)
  }
}