/*
 *  ImageFileSingleOutImpl.scala
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

package de.sciss.fscape.stream.impl

import akka.stream.Shape

trait ImageFileSingleOutImpl[S <: Shape] extends ImageFileOutImpl[S] {
  _: Handlers[S] =>

  protected def tryObtainSpec(): Boolean

  protected def tryObtainWinParams(): Boolean =
    imagesWritten == 0 && tryObtainSpec()

  protected def processWindow(): Unit = {
    closeImage()
    /*if (hOut.flush()) */ completeStage()
  }
}
