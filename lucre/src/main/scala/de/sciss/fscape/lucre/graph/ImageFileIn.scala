/*
 *  ImageFileIn.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package graph

import de.sciss.file.File
import de.sciss.fscape
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.lucre.UGenGraphBuilder.Input

object ImageFileIn {
  final case class Width(key: String) extends GE.Lazy {
    override def productPrefix = s"ImageFileIn$$Width"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val (_, spec) = ImageFileIn.getSpec(key, b)
      spec.width
    }
  }

  final case class Height(key: String) extends GE.Lazy {
    override def productPrefix = s"ImageFileIn$$Height"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val (_, spec) = ImageFileIn.getSpec(key, b)
      spec.height
    }
  }

  private def getSpec(key: String, b: UGenGraph.Builder): (File, ImageFile.Spec) = {
    val ub  = UGenGraphBuilder.get(b)
    val res = ub.requestInput(Input.Attribute(key)).peer
      .fold[(File, ImageFile.Spec)](sys.error(s"ImageFileIn missing attribute $key")) {
      case f: File =>
        f -> ImageFile.readSpec(f)
      case other =>
        sys.error(s"ImageFileIn - requires Artifact value, found $other")
    }
    res
  }
}
final case class ImageFileIn(key: String) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val (f, spec) = ImageFileIn.getSpec(key, b)
    fscape.graph.ImageFileIn(file = f, numChannels = spec.numChannels)
  }

  def width : GE = ImageFileIn.Width (key)
  def height: GE = ImageFileIn.Height(key)
}