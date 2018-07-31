/*
 *  ImageFileSeqOut.scala
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
package graph

import de.sciss.file.File
import de.sciss.fscape.UGen.Aux
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

final case class ImageFileSeqOut(in: GE, template: File, spec: ImageFile.Spec, indices: GE)
  extends UGenSource.ZeroOut {

  protected def makeUGens(implicit b: UGenGraph.Builder): Unit =
    unwrap(this, in.expand.outputs :+ indices.expand)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
    UGen.ZeroOut(this, inputs = args,
      aux = Aux.FileOut(template) :: Aux.ImageFileSpec(spec) :: Nil, isIndividual = true)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    val in :+ indices = args
    stream.ImageFileSeqOut(template = template, spec = spec, indices = indices.toInt, in = in.map(_.toDouble))
  }
}