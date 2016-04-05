/*
 *  DiskIn.scala
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
package graph

import de.sciss.file.File

case class DiskIn(file: File) extends UGenSource.SingleOut {
  protected def makeSignal: UGenIn = {
    val p = ugen.DiskIn(file)
    p.output
  }
}