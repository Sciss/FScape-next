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
import de.sciss.lucre.expr.graph.Constant
import de.sciss.lucre.expr.{Ex, IExpr, Model}
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.graph.impl.{ComponentImpl, FileInExpandedImpl, PathFieldValueExpandedImpl}
import de.sciss.lucre.swing.{Graph, PanelWithPathField}
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

  private final class Expanded[S <: Sys[S]](protected val w: AudioFileIn) extends FileInExpandedImpl[S] {
    protected def mkFormat(f: File): String = {
      val spec = AudioFile.readSpec(f)
      specToString(spec)
    }
  }

  final case class Value(w: AudioFileIn) extends Ex[File] {
    override def productPrefix: String = s"AudioFileIn$$Value" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, File] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[File]](w, PathField.keyValue)
      val value0    = valueOpt.fold[File](PathField.defaultValue)(_.expand[S].value)
      new PathFieldValueExpandedImpl[S](ws.component.pathField, value0).init()
    }
  }

  final case class Title(w: AudioFileIn) extends Ex[String] {
    override def productPrefix: String = s"AudioFileIn$$Title" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, String] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Constant(defaultTitle)).expand[S]
    }
  }

  final case class PathFieldVisible(w: AudioFileIn) extends Ex[Boolean] {
    override def productPrefix: String = s"AudioFileIn$$PathFieldVisible" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Boolean] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyPathFieldVisible)
      valueOpt.getOrElse(Constant(defaultPathFieldVisible)).expand[S]
    }
  }

  final case class FormatVisible(w: AudioFileIn) extends Ex[Boolean] {
    override def productPrefix: String = s"AudioFileIn$$FormatVisible" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Boolean] = {
      val valueOpt = ctx.getProperty[Ex[Boolean]](w, keyFormatVisible)
      valueOpt.getOrElse(Constant(defaultFormatVisible)).expand[S]
    }
  }

  private final case class Impl() extends AudioFileIn with ComponentImpl { w =>
    override def productPrefix: String = "AudioFileIn" // serialization

    protected def mkControl[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): Repr[S] =
      new Expanded[S](this).init()

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

  var title : Ex[String]
  def value : Model[File]
  
  var pathFieldVisible: Ex[Boolean]
  var formatVisible   : Ex[Boolean]
}
