/*
 *  Sheet1D.scala
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

/** Debugging utility that displays 1D "windows" of the input data as a spreadsheet or table view.
  *
  * '''Warning:''' window parameter modulation is currently not working correctly (issue #30)
  */
final case class Sheet1D(in: GE, size: GE, label: String = "sheet") extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, Vector(in.expand, size.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
    UGen.ZeroOut(this, inputs = args, adjuncts = Adjunct.String(label) :: Nil)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val Vec(in, size) = args
    stream.Sheet1D(in = in.toDouble, size = size.toInt, label = label)
  }
}
