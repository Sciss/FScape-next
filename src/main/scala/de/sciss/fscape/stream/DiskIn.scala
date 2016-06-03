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

package de.sciss.fscape.stream

import de.sciss.file._

object DiskIn {
  def apply(file: File)(implicit b: GBuilder, ctrl: Control): OutD = {
    val source = new AudioFileSource(file, ctrl)
    b.add(source).out
  }
}