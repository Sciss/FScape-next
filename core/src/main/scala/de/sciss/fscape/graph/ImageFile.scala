/*
 *  ImageFile.scala
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

import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer}

import scala.annotation.switch

object ImageFile {
  object Type {
    case object PNG extends Type { final val id = 0 }
    case object JPG extends Type { final val id = 1 }

    def apply(id: Int): Type = id match {
      case PNG.id => PNG
      case JPG.id => JPG
    }
  }
  sealed trait Type {
    def id: Int
  }

  object SampleFormat {
    case object Int8  extends SampleFormat { final val id = 0 }
    case object Int16 extends SampleFormat { final val id = 1 }

    /** Currently not supported (ImageIO) */
    case object Float extends SampleFormat { final val id = 2 }

    def apply(id: Int): SampleFormat = (id: @switch) match {
      case Int8  .id => Int8
      case Int16 .id => Int16
      case Float .id => Float
    }
  }
  sealed trait SampleFormat {
    def id: Int
  }

  object Spec {
    implicit object Serializer extends ImmutableSerializer[Spec] {
      def write(v: Spec, out: DataOutput): Unit = {
        import v._
        out.writeInt(fileType    .id)
        out.writeInt(sampleFormat.id)
        out.writeInt(width          )
        out.writeInt(height         )
        out.writeInt(numChannels    )
        out.writeInt(quality        )
      }

      def read(in: DataInput): Spec = {
        val fileType      = Type        (in.readInt())
        val sampleFormat  = SampleFormat(in.readInt())
        val width         = in.readInt()
        val height        = in.readInt()
        val numChannels   = in.readInt()
        val quality       = in.readInt()
        Spec(fileType, sampleFormat, width = width, height = height, numChannels = numChannels, quality = quality)
      }
    }
  }
  /** @param quality  only used for JPEG
    */
  final case class Spec(fileType     : Type         = Type.PNG,
                        sampleFormat : SampleFormat = SampleFormat.Int8,
                        width        : Int,
                        height       : Int,
                        numChannels  : Int,
                        quality      : Int = 80)
}
