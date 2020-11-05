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

import java.net.URI

import de.sciss.file.File
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.graph.ImageFile.SampleFormat
import de.sciss.lucre.{IExpr, Txn}
import de.sciss.lucre.expr.graph.{Const, Ex}
import de.sciss.lucre.expr.{Context, IControl, Model}
import de.sciss.lucre.swing.graph.impl.{ComponentImpl, FileInExpandedImpl, PathFieldValueExpandedImpl}
import de.sciss.lucre.swing.{Graph, PanelWithPathField, View}

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


  private final class Expanded[T <: Txn[T]](protected val peer: ImageFileIn) extends FileInExpandedImpl[T] {
    protected def mkFormat(uri: URI): String = {
      val spec = ImageFile.readSpec(uri)
      specToString(spec)
    }
  }

  final case class Value(w: ImageFileIn) extends Ex[URI] {
    type Repr[T <: Txn[T]] = IExpr[T, URI]

    override def productPrefix: String = s"ImageFileIn$$Value" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[URI]](w, PathField.keyValue)
      val value0    = valueOpt.fold[URI](PathField.defaultValue)(_.expand[T].value)
      new PathFieldValueExpandedImpl[T](ws.component.pathField, value0).init()
    }
  }

  final case class Title(w: ImageFileIn) extends Ex[String] {
    type Repr[T <: Txn[T]] = IExpr[T, String]

    override def productPrefix: String = s"ImageFileIn$$Title" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Const("Select Image Input File")).expand[T]
    }
  }

  final case class PathFieldVisible(w: ImageFileIn) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"ImageFileIn$$PathFieldVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyPathFieldVisible)
      valueOpt.getOrElse(Const(defaultPathFieldVisible)).expand[T]
    }
  }

  final case class FormatVisible(w: ImageFileIn) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"ImageFileIn$$FormatVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyFormatVisible)
      valueOpt.getOrElse(Const(defaultFormatVisible)).expand[T]
    }
  }

  private final case class Impl() extends ImageFileIn with ComponentImpl { w =>
    override def productPrefix: String = "ImageFileIn" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] =
      new Expanded[T](this).initComponent()

    object value extends Model[URI] {

      def apply(): Ex[URI] = Value(w)

      def update(value: Ex[URI]): Unit = {
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

  type Repr[T <: Txn[T]] = View.T[T, C] with IControl[T]

  var title: Ex[String]

  def value: Model[URI]

  var pathFieldVisible: Ex[Boolean]
  var formatVisible   : Ex[Boolean]
}
