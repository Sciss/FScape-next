/*
 *  ImageFileSeqOutImpl.scala
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
import de.sciss.file.File
import de.sciss.fscape.graph.ImageFileSeqIn.formatTemplate
import de.sciss.fscape.stream.impl.Handlers.InIMain

trait ImageFileSeqOutImpl[S <: Shape] extends ImageFileOutImpl[S] {
  _: Handlers[S] =>

  // ---- abstract ----

  protected def template: File

  protected def hIndices: InIMain

  protected def tryObtainSpec(): Boolean

  protected def tryObtainWinParams(): Boolean = {
    val ok = hIndices.hasNext && tryObtainSpec()
    if (ok) {
      val idx = hIndices.next()
      val f   = formatTemplate(template, idx)
      openImage(f)
    }
    ok
  }

  protected def processWindow(): Unit = {
    closeImage()
    if (hIndices.isDone /*&& hOut.flush()*/) completeStage()
  }
}
