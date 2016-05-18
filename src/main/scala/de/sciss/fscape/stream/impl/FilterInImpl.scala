/*
 *  FilterInImpl.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.FanInShape
import akka.stream.stage.GraphStageLogic
import de.sciss.fscape.stream.{BufLike, Control}

trait FilterInImpl[Out >: Null <: BufLike, Shape <: FanInShape[Out]] {
  _: GraphStageLogic =>

  // ---- abstract ----

  protected def shape: Shape

  protected def ctrl: Control

  protected def process(): Unit

  protected def canRead: Boolean

  protected def readIns(): Unit

  protected def freeInputBuffers (): Unit
  protected def freeOutputBuffers(): Unit
}
