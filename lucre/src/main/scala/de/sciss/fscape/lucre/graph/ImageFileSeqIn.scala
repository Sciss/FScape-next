/*
 *  ImageFileSeqIn.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package graph

import de.sciss.file._
import de.sciss.fscape
import de.sciss.fscape.graph.ImageFileSeqIn.formatTemplate
import de.sciss.fscape.graph.{ArithmSeq, Constant, DC, ImageFile}
import de.sciss.fscape.lucre.UGenGraphBuilder.Input

import scala.annotation.tailrec

object ImageFileSeqIn {
  final case class Width(key: String, indices: GE) extends GE.Lazy {
    override def productPrefix = s"ImageFileSeqIn$$Width"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val (_, spec) = ImageFileSeqIn.getSpec(key, indices)
      spec.width
    }
  }

  final case class Height(key: String, indices: GE) extends GE.Lazy {
    override def productPrefix = s"ImageFileSeqIn$$Height"

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
      val (_, spec) = ImageFileSeqIn.getSpec(key, indices)
      spec.height
    }
  }

  @tailrec
  private def tryResolveInt(in: GE)(implicit b: UGenGraph.Builder): Option[Int] =
    in match {
      case Constant(c)            => Some(c.toInt)
      case ArithmSeq(start, _, _) => tryResolveInt(start)
      case DC(in0)                => tryResolveInt(in0)
      case attr: Attribute        =>
        val exp = attr.expand
       tryResolveInt(exp)
      case _ => None
    }

  private def getSpec(key: String, indices: GE)(implicit b: UGenGraph.Builder): (File, ImageFile.Spec) = {
    val ub  = UGenGraphBuilder.get(b)
    val res = ub.requestInput(Input.Attribute(key)).peer
      .fold[(File, ImageFile.Spec)](sys.error(s"ImageFileSeqIn missing attribute $key")) {
      case template: File =>
        // XXX TODO this is a bit cheesy. We try to resolve
        // the start index using some tricks, and if we fail,
        // find a child that matches the template.
        val idx0Opt = tryResolveInt(indices)
        val idx0 = idx0Opt.getOrElse {
          template.parentOption.fold(1) { dir =>
            val n     = template.name
            val i     = n.indexOf("%d")
            val pre   = if (i < 0) n  else n.substring(0, i)
            val post  = if (i < 0) "" else n.substring(i + 2)
            dir.children.iterator.map(_.name).collectFirst {
              case n1 if n1.startsWith(pre) && n1.endsWith(post) =>
                val idxS = n1.substring(i, n1.length - post.length)
                idxS.toInt
            } .getOrElse(1)
          }
        }
        val f = formatTemplate(template, idx0)
        template -> ImageFile.readSpec(f)
      case other =>
        sys.error(s"ImageFileSeqIn - requires Artifact value, found $other")
    }
    res
  }
}
final case class ImageFileSeqIn(key: String, indices: GE) extends GE.Lazy {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val (f, spec) = ImageFileSeqIn.getSpec(key, indices)
    fscape.graph.ImageFileSeqIn(template = f, numChannels = spec.numChannels, indices = indices)
  }

  def width : GE = ImageFileSeqIn.Width (key, indices)
  def height: GE = ImageFileSeqIn.Height(key, indices)
}