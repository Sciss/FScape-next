/*
 *  Masking2D.scala
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

package de.sciss.fscape.graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}
import de.sciss.fscape.{GE, UGen, UGenGraph, UGenIn, UGenInLike, UGenSource, stream}

import scala.collection.immutable.{IndexedSeq => Vec}

object Masking2D extends ProductReader[Masking2D] {
  override def read(in: RefMapIn, key: String, arity: Int): Masking2D = {
    require (arity == 8)
    val _fg          = in.readGE()
    val _bg          = in.readGE()
    val _rows        = in.readGE()
    val _columns     = in.readGE()
    val _threshNoise = in.readGE()
    val _threshMask  = in.readGE()
    val _blurRows    = in.readGE()
    val _blurColumns = in.readGE()
    new Masking2D(_fg, _bg, _rows, _columns, _threshNoise, _threshMask, _blurRows, _blurColumns)
  }
}
final case class Masking2D(fg         : GE,
                           bg         : GE,
                           rows       : GE,
                           columns    : GE,
                           threshNoise: GE,
                           threshMask : GE,
                           blurRows   : GE,
                           blurColumns: GE,
                          )
  extends UGenSource.SingleOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(fg.expand, bg.expand, rows.expand, columns.expand,
      threshNoise.expand, threshMask.expand, blurRows.expand, blurColumns.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGen.SingleOut =
    UGen.SingleOut(this, inputs = args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(fg, bg, rows, columns, threshNoise, threshMask, blurRows, blurColumns) = args
    stream.Masking2D(fg = fg.toDouble, bg = bg.toDouble, rows = rows.toInt, columns = columns.toInt,
      threshNoise = threshNoise.toDouble, threshMask = threshMask.toDouble,
      blurRows = blurRows.toInt, blurColumns = blurColumns.toInt)
  }
}
