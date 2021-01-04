/*
 *  ImageFileIn.scala
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

package de.sciss.fscape.lucre
package graph

import java.net.URI

import de.sciss.fscape
import de.sciss.fscape.{GE, UGenGraph, UGenInLike}
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.lucre.UGenGraphBuilder.Input

object ImageFileIn {
  final case class Width(key: String) extends GE.Lazy {
    override def productPrefix = s"ImageFileIn$$Width"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val (_, spec) = ImageFileIn.getSpec(key)
      spec.width
    }
  }

  final case class Height(key: String) extends GE.Lazy {
    override def productPrefix = s"ImageFileIn$$Height"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val (_, spec) = ImageFileIn.getSpec(key)
      spec.height
    }
  }

  private def getSpec(key: String)(implicit b: UGenGraph.Builder): (URI, ImageFile.Spec) = {
    val ub  = UGenGraphBuilder.get(b)
    val res = ub.requestInput(Input.Attribute(key)).peer
      .fold[(URI, ImageFile.Spec)](sys.error(s"ImageFileIn missing attribute $key")) {
      case f: URI =>
        f -> ImageFile.readSpec(f)
      case other =>
        sys.error(s"ImageFileIn - requires Artifact value, found $other")
    }
    res
  }
}
final case class ImageFileIn(key: String) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val (f, spec) = ImageFileIn.getSpec(key)
    fscape.graph.ImageFileIn(file = f, numChannels = spec.numChannels)
  }

  def width : GE = ImageFileIn.Width (key)
  def height: GE = ImageFileIn.Height(key)
}