/*
 *  AudioFileOutExpandedImpl.scala
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

package de.sciss.lucre.swing.graph.impl

import de.sciss.desktop
import de.sciss.desktop.{FileDialog, TextFieldWithPaint}
import de.sciss.file.File
import de.sciss.fscape.lucre.graph.{AudioFileOut => UAudioFileOut}
import de.sciss.lucre.expr.Ex
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.graph.{AudioFileOut, PathField}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, deferTx}
import de.sciss.swingplus.{ComboBox, ListView}
import de.sciss.synth.io
import de.sciss.synth.io.AudioFileType
import javax.swing.JList

import scala.swing.event.ValueChanged
import scala.swing.{Orientation, SequentialContainer, Swing}

final class AudioFileOutExpandedImpl[S <: Sys[S]](protected val w: AudioFileOut,
                                                  fileTypeVisible     : Boolean,
                                                  sampleFormatVisible : Boolean,
                                                  sampleRateVisible   : Boolean)
  extends View[S] with ComponentHolder[AudioFileOut.Peer] with ComponentExpandedImpl[S] {

  type C = AudioFileOut.Peer

  override def init()(implicit tx: S#Tx, ctx: Ex.Context[S]): this.type = {
    val valueOpt  = ctx.getProperty[Ex[File   ]](w, PathField   .keyValue       ).map(_.expand[S].value)
    val titleOpt  = ctx.getProperty[Ex[String ]](w, PathField   .keyTitle       ).map(_.expand[S].value)
    val fileTpeIdx= ctx.getProperty[Ex[Int    ]](w, AudioFileOut.keyFileType    ).fold(AudioFileOut.defaultFileType     )(_.expand[S].value)
    val smpFmtIdx = ctx.getProperty[Ex[Int    ]](w, AudioFileOut.keySampleFormat).fold(AudioFileOut.defaultSampleFormat )(_.expand[S].value)
    val smpRate   = ctx.getProperty[Ex[Double ]](w, AudioFileOut.keySampleRate  ).fold(AudioFileOut.defaultSampleRate   )(_.expand[S].value)

    deferTx {
      val c: C = new AudioFileOut.Peer with SequentialContainer.Wrapper  {
        private[this] val fmc = {
          val res = new TextFieldWithPaint(27)
          res.editable  = false
          res.focusable = false
          res
        }

        val pathField: desktop.PathField = {
          val res = new desktop.PathField
          res.mode = FileDialog.Save
          valueOpt.foreach(res.value = _)
          titleOpt.foreach(res.title = _)
          res.reactions += {
            case ValueChanged(_) => // ???
          }
          res
        }

        def mkCombo[A](items: Seq[A])(fmt: Any => String): ComboBox[A] = {
          val res = new ComboBox[A](items)
          val jr = new ListCellRendererDelegate {
            def rendererDelegate(list: JList[_], value: AnyRef, index: Int, isSelected: Boolean,
                                 cellHasFocus: Boolean): AnyRef = fmt(value)
          }
          res.renderer = ListView.Renderer.wrap(jr)
          res
        }

        val fileTypeComboBox: ComboBox[AudioFileType] = {
          val items = List.tabulate(UAudioFileOut.maxFileTypeId + 1)(UAudioFileOut.fileType)
          val res = mkCombo(items) {
            case x: AudioFileType => x.name
            case x                => x.toString
          }
          res.selection.index = fileTpeIdx
          res
        }

        val sampleFormatComboBox: ComboBox[io.SampleFormat] = {
          val items = List.tabulate(UAudioFileOut.maxSampleFormatId + 1)(UAudioFileOut.sampleFormat)
          val res = mkCombo(items) {
            case x: io.SampleFormat => AudioFileOut.formatFormat(x)
            case x                  => x.toString
          }
          res.selection.index = smpFmtIdx
          res
        }

        val sampleRateComboBox: ComboBox[Double] = {
          val items = List[Double](44100, 48000, 88200, 96000, 176400, 192000)
          val res = mkCombo(items) {
            case x: Double => x.toInt.toString
            case x         => x.toString
          }
          res.item = smpRate
          res
        }

        private[this] val fb = {
          val res = new scala.swing.BoxPanel(Orientation.Horizontal)
          res.contents += fmc
          res.contents += Swing.HStrut(pathField.button.preferredSize.width)
          res
        }

        override lazy val peer: javax.swing.JPanel = {
          val p = new javax.swing.JPanel with SuperMixin {
            override def getBaseline(width: Int, height: Int): Int = {
              val pfj = pathField.peer
              val d   = pfj.getPreferredSize
              val res = pfj.getBaseline(d.width, d.height)
              res + pfj.getY
            }
          }
          val l = new javax.swing.BoxLayout(p, Orientation.Vertical.id)
          p.setLayout(l)
          p
        }

        contents += pathField
        contents += fb
      }

      component = c
    }
    super.init()
  }
}