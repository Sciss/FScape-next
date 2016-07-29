/*
 *  ImageFileOut.scala
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
import de.sciss.fscape.stream.StreamIn

import scala.collection.immutable.{IndexedSeq => Vec}

object ImageFileOut {
  object FileType {
    case object PNG extends FileType
    case object JPG extends FileType
  }
  sealed trait FileType

  object SampleFormat {
    case object Int8  extends SampleFormat
    case object Int16 extends SampleFormat
    case object Float extends SampleFormat
  }
  sealed trait SampleFormat

  final case class Spec(fileType     : FileType     = FileType.PNG,
                        sampleFormat : SampleFormat = SampleFormat.Int8,
                        width        : Int,
                        height       : Int,
                        numChannels  : Int)
}
final case class ImageFileOut(file: File, spec: ImageFileOut.Spec, in: GE) extends UGenSource.ZeroOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): Unit = unwrap(in.expand.outputs)

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): Unit =
    UGen.ZeroOut(this, inputs = args, rest = file, isIndividual = true)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): Unit = {
    stream.ImageFileOut(file = file, spec = spec, in = args.map(_.toDouble))
  }
}