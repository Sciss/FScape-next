/*
 *  AudioFileIn.scala
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

import de.sciss.audiowidgets.AxisFormat
import de.sciss.file.File
import de.sciss.lucre.expr.graph.{Const, Ex}
import de.sciss.lucre.expr.{Context, IControl, Model}
import de.sciss.lucre.swing.graph.impl.{ComponentImpl, FileInExpandedImpl, PathFieldValueExpandedImpl}
import de.sciss.lucre.swing.{Graph, PanelWithPathField, View}
import de.sciss.lucre.{IExpr, Txn}
import de.sciss.synth.io
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

object AudioFileIn {
  def apply(): AudioFileIn = Impl()

  private lazy val timeFmt = AxisFormat.Time(hours = false, millis = true)

  def specToString(spec: AudioFileSpec): String = {
    import spec._
    val smpFmt  = formatToString(sampleFormat)
    val channels = spec.numChannels match {
      case 1 => "mono"
      case 2 => "stereo"
      case n => s"$n-chan."
    }
    val sr  = f"${sampleRate/1000}%1.1f"
    val dur = timeFmt.format(numFrames.toDouble / sampleRate)
    val txt = s"${fileType.name}, $channels $smpFmt $sr kHz, $dur"
    txt
  }

  def formatToString(smp: io.SampleFormat): String = {
    val smpTpe = smp match {
      case io.SampleFormat.Float | io.SampleFormat.Double => "float"
      case io.SampleFormat.UInt8                          => "uint"
      case _                                              => "int"
    }
    val txt = s"${smp.bitsPerSample}-bit $smpTpe"
    txt
  }

  private final class Expanded[T <: Txn[T]](protected val peer: AudioFileIn) extends FileInExpandedImpl[T] {
    protected def mkFormat(f: File): String = {
      val spec = AudioFile.readSpec(f)
      specToString(spec)
    }
  }

  final case class Value(w: AudioFileIn) extends Ex[File] {
    type Repr[T <: Txn[T]] = IExpr[T, File]

    override def productPrefix: String = s"AudioFileIn$$Value" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[T]
      val valueOpt  = ctx.getProperty[Ex[File]](w, PathField.keyValue)
      val value0    = valueOpt.fold[File](PathField.defaultValue)(_.expand[T].value)
      new PathFieldValueExpandedImpl[T](ws.component.pathField, value0).init()
    }
  }

  final case class Title(w: AudioFileIn) extends Ex[String] {
    type Repr[T <: Txn[T]] = IExpr[T, String]

    override def productPrefix: String = s"AudioFileIn$$Title" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Const(defaultTitle)).expand[T]
    }
  }

  final case class PathFieldVisible(w: AudioFileIn) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"AudioFileIn$$PathFieldVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyPathFieldVisible)
      valueOpt.getOrElse(Const(defaultPathFieldVisible)).expand[T]
    }
  }

  final case class FormatVisible(w: AudioFileIn) extends Ex[Boolean] {
    type Repr[T <: Txn[T]] = IExpr[T, Boolean]

    override def productPrefix: String = s"AudioFileIn$$FormatVisible" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyFormatVisible)
      valueOpt.getOrElse(Const(defaultFormatVisible)).expand[T]
    }
  }

  private final case class Impl() extends AudioFileIn with ComponentImpl { w =>
    override def productPrefix: String = "AudioFileIn" // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] =
      new Expanded[T](this).initComponent()

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

  private[graph] val keyPathFieldVisible  = "pathFieldVisible"
  private[graph] val keyFormatVisible     = "formatVisible"
  private[graph] val defaultTitle         = "Select Audio Input File"
  
  private[graph] val defaultPathFieldVisible  = true
  private[graph] val defaultFormatVisible     = true

  type Peer = PanelWithPathField
}
trait AudioFileIn extends Component {
  type C = AudioFileIn.Peer

  type Repr[T <: Txn[T]] = View.T[T, C] with IControl[T]

  var title : Ex[String]
  def value : Model[File]
  
  var pathFieldVisible: Ex[Boolean]
  var formatVisible   : Ex[Boolean]
}
