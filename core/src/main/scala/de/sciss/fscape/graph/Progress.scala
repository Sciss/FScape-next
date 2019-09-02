/*
 *  Progress.scala
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

import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen that contributes to the progress monitoring of a graph.
  * It is possible to instantiate multiple instances of this UGen,
  * in which cases their individual progress reports will simply
  * be added up (and clipped to the range from zero to one).
  *
  * @param in     progress fraction from zero to one
  * @param trig   trigger that causes the UGen to submit a snapshot
  *               of the progress to the control instance.
  * @param label  the label can be used to distinguish the
  *               contributions of different progress UGens
  */
final case class Progress(in: GE, trig: GE, label: String = "render")
  extends UGenSource.ZeroOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, Vector(in.expand, trig.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
    UGen.ZeroOut(this, inputs = args, adjuncts = Adjunct.String(label) :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val Vec(in, trig) = args
    stream.Progress(in = in.toDouble, trig = trig.toInt, label = label)
  }
}