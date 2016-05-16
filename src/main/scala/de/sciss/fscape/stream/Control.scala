/*
 *  Control.scala
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

package de.sciss.fscape.stream

object Control {
  def apply(bufSize: Int): Control = ???
}
trait Control {
  def bufSize: Int

  def borrowBufD(): BufD
  def borrowBufI(): BufI

  def returnBufD(buf: BufD): Unit
  def returnBufI(buf: BufI): Unit
}