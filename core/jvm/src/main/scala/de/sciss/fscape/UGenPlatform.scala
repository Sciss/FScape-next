package de.sciss.fscape

import de.sciss.file._
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.graph.ImageFile
import de.sciss.serial.DataOutput

trait UGenPlatform {
  trait AdjunctPlatform {
    final case class FileOut(peer: File) extends Adjunct {
      def write(out: DataOutput): Unit = {
        out.writeByte(0)
        out.writeUTF(peer.path)
      }
    }

    final case class FileIn(peer: File) extends Adjunct {
      def write(out: DataOutput): Unit = {
        out.writeByte(1)
        out.writeUTF( peer.path)
        out.writeLong(peer.lastModified())
        out.writeLong(peer.length())
      }
    }

    final case class ImageFileSpec(peer: ImageFile.Spec) extends Adjunct {
      def write(out: DataOutput): Unit = {
        out.writeByte(11)
        ImageFile.Spec.format.write(peer, out)
      }
    }
  }
}
