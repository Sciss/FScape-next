/*
 *  ImageFileIn.scala
 *  (LucreSwing)
 *
 *  Copyright (c) 2014-2019 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.lucre.swing.graph

import de.sciss.file.File
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.graph.ImageFile.SampleFormat
import de.sciss.lucre.expr.graph.Constant
import de.sciss.lucre.expr.{Ex, IExpr, Model}
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.graph.impl.{ComponentImpl, FileInExpandedImpl, PathFieldValueExpandedImpl}
import de.sciss.lucre.swing.{Graph, PanelWithPathField}

object ImageFileIn {
  def apply(): ImageFileIn = Impl()

  def specToString(spec: ImageFile.Spec): String = {
    import spec._

    val smpFmt = formatToString(sampleFormat)
    val channels = numChannels match {
      case 1 => "grayscale"
      case 3 => "RGB"
      case 4 => "RGBA"
      case n => s"$n-chan."
    }
    val size = s"$width\u00D7$height"
    val txt = s"${fileType.name}, $channels $smpFmt, $size"
    txt
  }

  def formatToString(smp: SampleFormat): String = {
    val smpTpe = smp match {
      case SampleFormat.Float => "float"
      case _                  => "int"
    }
    val txt = s"${smp.bitsPerSample}-bit $smpTpe"
    txt
  }


  private final class Expanded[S <: Sys[S]](protected val w: ImageFileIn) extends FileInExpandedImpl[S] {
    protected def mkFormat(f: File): String = {
      val spec = ImageFile.readSpec(f)
      specToString(spec)
    }
  }

  final case class Value(w: ImageFileIn) extends Ex[File] {
    override def productPrefix: String = s"ImageFileIn$$Value" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, File] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[File]](w, PathField.keyValue)
      val value0    = valueOpt.fold[File](PathField.defaultValue)(_.expand[S].value)
      new PathFieldValueExpandedImpl[S](ws.component.pathField, value0).init()
    }
  }

  final case class Title(w: ImageFileIn) extends Ex[String] {
    override def productPrefix: String = s"ImageFileIn$$Title" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, String] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Constant("Select Image Input File")).expand[S]
    }
  }

  final case class PathFieldVisible(w: ImageFileIn) extends Ex[Boolean] {
    override def productPrefix: String = s"ImageFileIn$$PathFieldVisible" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Boolean] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyPathFieldVisible)
      valueOpt.getOrElse(Constant(defaultPathFieldVisible)).expand[S]
    }
  }

  final case class FormatVisible(w: ImageFileIn) extends Ex[Boolean] {
    override def productPrefix: String = s"ImageFileIn$$FormatVisible" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Boolean] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyFormatVisible)
      valueOpt.getOrElse(Constant(defaultFormatVisible)).expand[S]
    }
  }

  private final case class Impl() extends ImageFileIn with ComponentImpl { w =>
    override def productPrefix: String = "ImageFileIn" // serialization

    protected def mkControl[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): Repr[S] =
      new Expanded[S](this).initComponent()

    object value extends Model[File] {
      def apply(): Ex[File] = Value(w)

      def update(value: Ex[File]): Unit = {
        val b = Graph.builder
        b.putProperty(w, PathField.keyValue, value)
      }
    }

    def title: Ex[String] = Title(this)

    def title_=(value: Ex[String]): Unit = {
      val b = Graph.builder
      b.putProperty(this, PathField.keyTitle, value)
    }

    def pathFieldVisible: Ex[Boolean] = PathFieldVisible(this)

    def pathFieldVisible_=(value: Ex[Boolean]): Unit = {
      val b = Graph.builder
      b.putProperty(this, keyPathFieldVisible, value)
    }

    def formatVisible: Ex[Boolean] = FormatVisible(this)

    def formatVisible_=(value: Ex[Boolean]): Unit = {
      val b = Graph.builder
      b.putProperty(this, keyFormatVisible, value)
    }
  }

  private[graph] def keyPathFieldVisible  : String  = AudioFileIn.keyPathFieldVisible
  private[graph] def keyFormatVisible     : String  = AudioFileIn.keyFormatVisible

  private[graph] def defaultPathFieldVisible: Boolean = AudioFileIn.defaultPathFieldVisible
  private[graph] def defaultFormatVisible   : Boolean = AudioFileIn.defaultFormatVisible

  type Peer = PanelWithPathField
}
trait ImageFileIn extends Component {
  type C = ImageFileIn.Peer

  var title : Ex[String]
  def value : Model[File]

  var pathFieldVisible: Ex[Boolean]
  var formatVisible   : Ex[Boolean]
}
