/*
 *  AudioFileOut.scala
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
import de.sciss.lucre.expr.graph.Constant
import de.sciss.lucre.expr.{Ex, IExpr, Model}
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.graph.impl.{AudioFileOutExpandedImpl, ComboBoxIndexExpandedImpl, ComponentImpl, PathFieldValueExpandedImpl}
import de.sciss.lucre.swing.{Graph, PanelWithPathField}
import de.sciss.swingplus.{ComboBox => _ComboBox}
import de.sciss.synth.io
import de.sciss.synth.io.AudioFileType

object AudioFileOut {
  def apply(fileTypeVisible     : Boolean = true,
            sampleFormatVisible : Boolean = true,
            sampleRateVisible   : Boolean = false): AudioFileOut =
    Impl(fileTypeVisible = fileTypeVisible, sampleFormatVisible = sampleFormatVisible, sampleRateVisible = sampleRateVisible)

  def formatFormat(smp: io.SampleFormat): String = {
    val smpTpe = smp match {
      case io.SampleFormat.Float | io.SampleFormat.Double => "float"
      case _ => "int"
    }
    val txt = s"${smp.bitsPerSample}-bit $smpTpe"
    txt
  }

  final case class Value(w: AudioFileOut) extends Ex[File] {
    override def productPrefix: String = s"AudioFileOut$$Value" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, File] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[File]](w, PathField.keyValue)
      val value0    = valueOpt.fold[File](PathField.defaultValue)(_.expand[S].value)
      new PathFieldValueExpandedImpl[S](ws.component.pathField, value0).init()
    }
  }

  final case class FileType(w: AudioFileOut) extends Ex[Int] {
    override def productPrefix: String = s"AudioFileOut$$FileType" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Int] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keyFileType)
      val value0    = valueOpt.fold[Int](defaultFileType)(_.expand[S].value)
      new ComboBoxIndexExpandedImpl[S, AudioFileType](ws.component.fileTypeComboBox, value0).init()
    }
  }

  final case class SampleFormat(w: AudioFileOut) extends Ex[Int] {
    override def productPrefix: String = s"AudioFileOut$$SampleFormat" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Int] = {
      import ctx.{cursor, targets}
      val ws        = w.expand[S]
      val valueOpt  = ctx.getProperty[Ex[Int]](w, keySampleFormat)
      val value0    = valueOpt.fold[Int](defaultSampleFormat)(_.expand[S].value)
      new ComboBoxIndexExpandedImpl[S, io.SampleFormat](ws.component.sampleFormatComboBox, value0).init()
    }
  }

  final case class Title(w: AudioFileOut) extends Ex[String] {
    override def productPrefix: String = s"AudioFileOut$$Title" // serialization

    def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, String] = {
      val valueOpt = ctx.getProperty[Ex[String]](w, PathField.keyTitle)
      valueOpt.getOrElse(Constant(defaultTitle)).expand[S]
    }
  }

  private final case class Impl(fileTypeVisible     : Boolean,
                                sampleFormatVisible : Boolean,
                                sampleRateVisible   : Boolean)
    extends AudioFileOut with ComponentImpl { w =>

    override def productPrefix: String = "AudioFileOut" // serialization

    protected def mkControl[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): Repr[S] =
      new AudioFileOutExpandedImpl[S](this,
        fileTypeVisible     = fileTypeVisible,
        sampleFormatVisible = sampleFormatVisible,
        sampleRateVisible   = sampleRateVisible
      ).init()

    object value extends Model[File] {
      def apply(): Ex[File] = Value(w)

      def update(value: Ex[File]): Unit = {
        val b = Graph.builder
        b.putProperty(w, PathField.keyValue, value)
      }
    }

    object fileType extends Model[Int] {
      def apply(): Ex[Int] = FileType(w)

      def update(value: Ex[Int]): Unit = {
        val b = Graph.builder
        b.putProperty(w, keyFileType, value)
      }
    }

    object sampleFormat extends Model[Int] {
      def apply(): Ex[Int] = SampleFormat(w)

      def update(value: Ex[Int]): Unit = {
        val b = Graph.builder
        b.putProperty(w, keySampleFormat, value)
      }
    }

    object sampleRate extends Model[Double] {
      def apply(): Ex[Double] = ??? // SampleRate(w)

      def update(value: Ex[Double]): Unit = {
        val b = Graph.builder
        b.putProperty(w, keySampleRate, value)
      }
    }

    def title: Ex[String] = Title(this)

    def title_=(value: Ex[String]): Unit = {
      val b = Graph.builder
      b.putProperty(this, PathField.keyTitle, value)
    }
  }

  private[graph] final val keyFileType          = "fileType"
  private[graph] final val keySampleFormat      = "sampleFormat"
  private[graph] final val keySampleRate        = "sampleRate"

  private[graph] final val defaultFileType      = 0 // AIFF
  private[graph] final val defaultSampleFormat  = 1 // int24
  private[graph] final val defaultSampleRate    = 44100.0
  private[graph] final val defaultTitle         = "Select Audio Output File"

  abstract class Peer extends PanelWithPathField {
    def fileTypeComboBox    : _ComboBox[AudioFileType]
    def sampleFormatComboBox: _ComboBox[io.SampleFormat]
    def sampleRateComboBox  : _ComboBox[Double]
  }
}
trait AudioFileOut extends Component {
  type C = AudioFileOut.Peer

  var title       : Ex[String]
  def value       : Model[File]

  def fileType    : Model[Int]
  def sampleFormat: Model[Int]
  def sampleRate  : Model[Double]
}
