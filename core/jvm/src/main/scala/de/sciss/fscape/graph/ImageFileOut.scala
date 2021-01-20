/*
 *  ImageFileOut.scala
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

package de.sciss.fscape
package graph

import de.sciss.fscape.Graph.{ProductReader, RefMapIn}

import java.net.URI
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object ImageFileOut extends ProductReader[ImageFileOut] {
  override def read(in: RefMapIn, key: String, arity: Int): ImageFileOut = {
    require (arity == 3)
    val _in   = in.readGE()
    val _file = in.readURI()
    val _spec = in.readProductT[ImageFile.Spec]()
    new ImageFileOut(_in, _file, _spec)
  }
}
final case class ImageFileOut(in: GE, file: URI, spec: ImageFile.Spec) extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = unwrap(this, in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit = {
    UGen.ZeroOut(this, inputs = args, adjuncts = Adjunct.FileOut(file) :: Adjunct.ImageFileSpec(spec) :: Nil, isIndividual = true)
    ()
  }

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    stream.ImageFileOut(uri = file, spec = spec, in = args.map(_.toDouble))
  }
}