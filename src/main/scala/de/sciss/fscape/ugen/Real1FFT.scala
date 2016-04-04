/*
 *  Real1FFT.scala
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

package de.sciss.fscape
package ugen

/** Test */
case class UGenInImpl() extends UGenIn

class Real1FFT extends UGen {
  val output = UGenInImpl()
}