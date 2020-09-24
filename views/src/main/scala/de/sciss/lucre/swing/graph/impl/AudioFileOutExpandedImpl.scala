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
import de.sciss.file._
import de.sciss.fscape.lucre.graph.{AudioFileOut => UAudioFileOut}
import de.sciss.lucre.Txn
import de.sciss.lucre.expr.Context
import de.sciss.lucre.expr.graph.Ex
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.graph.{AudioFileIn, AudioFileOut, PathField}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.swingplus.{ComboBox, ListView}
import de.sciss.synth.io
import de.sciss.synth.io.AudioFileType
import javax.swing.{JList, ListCellRenderer}

import scala.swing.Reactions.Reaction
import scala.swing.event.{SelectionChanged, ValueChanged}
import scala.swing.{Orientation, SequentialContainer, Swing}

final class AudioFileOutExpandedImpl[T <: Txn[T]](protected val peer: AudioFileOut)
  extends View[T] with ComponentHolder[AudioFileOut.Peer] with ComponentExpandedImpl[T] {

  type C = AudioFileOut.Peer

  override def initComponent()(implicit tx: T, ctx: Context[T]): this.type = {
    val pathOpt   = ctx.getProperty[Ex[File   ]](peer, PathField   .keyValue       ).map(_.expand[T].value)
    val titleOpt  = ctx.getProperty[Ex[String ]](peer, PathField   .keyTitle       ).map(_.expand[T].value)
    val fileTpeIdx= ctx.getProperty[Ex[Int    ]](peer, AudioFileOut.keyFileType    ).fold(AudioFileOut.defaultFileType     )(_.expand[T].value)
    val smpFmtIdx = ctx.getProperty[Ex[Int    ]](peer, AudioFileOut.keySampleFormat).fold(AudioFileOut.defaultSampleFormat )(_.expand[T].value)
    val smpRate   = ctx.getProperty[Ex[Double ]](peer, AudioFileOut.keySampleRate  ).fold(AudioFileOut.defaultSampleRate   )(_.expand[T].value)

    def getBoolean(key: String, default: => Boolean): Boolean =
      ctx.getProperty[Ex[Boolean]](peer, key).fold(default)(_.expand[T].value)

    val pathFieldVisible    = getBoolean(AudioFileOut.keyPathFieldVisible   , AudioFileOut.defaultPathFieldVisible    )
    val fileTypeVisible     = getBoolean(AudioFileOut.keyFileTypeVisible    , AudioFileOut.defaultFileTypeVisible     )
    val sampleFormatVisible = getBoolean(AudioFileOut.keySampleFormatVisible, AudioFileOut.defaultSampleFormatVisible )
    val sampleRateVisible   = getBoolean(AudioFileOut.keySampleRateVisible  , AudioFileOut.defaultSampleRateVisible   )

    deferTx {
      val c: C = new AudioFileOut.Peer with SequentialContainer.Wrapper  {
        def updateFormat(): Unit =
          pathField.paint = if (pathField.valueOption.exists(_.exists())) {
            Some(TextFieldWithPaint.BlueOverlay)
          } else {
            None
          }

        override def enabled_=(b: Boolean): Unit = {
          super.enabled_=(b)
          if (pathFieldVisible    ) pathField           .enabled = b
          if (fileTypeVisible     ) fileTypeComboBox    .enabled = b
          if (sampleFormatVisible ) sampleFormatComboBox.enabled = b
          if (sampleRateVisible   ) sampleRateComboBox  .enabled = b
        }

        lazy val pathField: desktop.PathField = {
          val res = new desktop.PathField
          res.mode = FileDialog.Save
          pathOpt .foreach(res.value = _)
          titleOpt.foreach(res.title = _)
          res
        }

        val pathReaction: Reaction = {
          case ValueChanged(_) => updateFormat()  // XXX TODO --- refinement: adjust fileType selection
        }

        def listenToPath(): Unit = if (pathFieldVisible)
          pathField.reactions += pathReaction

        def deafToPath(): Unit = if (pathFieldVisible)
          pathField.reactions -= pathReaction

        def mkCombo[A](items: Seq[A])(fmt: A => String): ComboBox[A] = {
          val res = new ComboBox[A](items)
          val jr: ListCellRenderer[A] = new ListCellRendererDelegate[A, Any](res.renderer.peer
            .asInstanceOf[ListCellRenderer[Any]]) {

            def rendererDelegate(list: JList[_ <: A], value: A, index: Int, isSelected: Boolean,
                                 cellHasFocus: Boolean): AnyRef = fmt(value)
          }
          res.renderer = ListView.Renderer.wrap(jr)
          res
        }

        lazy val fileTypeComboBox: ComboBox[AudioFileType] = {
          val items = List.tabulate(UAudioFileOut.maxFileTypeId + 1)(UAudioFileOut.fileType)
          val res = mkCombo(items) {
            case x: AudioFileType => x.name
            case x                => x.toString
          }
          res.selection.index = fileTpeIdx
          res.reactions += {
            case SelectionChanged(_) =>
              Option(res.selection.item).foreach { tpe =>
                if (pathFieldVisible) {
                  for {
                    f   <- pathField.valueOption
                    if !tpe.extensions.contains(f.extL)
                  } {
                    deafToPath()
                    val fNew = f.replaceExt(tpe.extension)
                    pathField.value = fNew
                    updateFormat()
                    listenToPath()
                  }
                }
              }
          }
          res
        }

        lazy val sampleFormatComboBox: ComboBox[io.SampleFormat] = {
          val items = List.tabulate(UAudioFileOut.maxSampleFormatId + 1)(UAudioFileOut.sampleFormat)
          val res = mkCombo(items) {
            case x: io.SampleFormat => AudioFileIn.formatToString(x)
            case x                  => x.toString
          }
          res.selection.index = smpFmtIdx
          res
        }

        lazy val sampleRateComboBox: ComboBox[Double] = {
          val items = List[Double](44100, 48000, 88200, 96000, 176400, 192000)
          val res = mkCombo(items)(_.toInt.toString)
          res.item = smpRate
          res
        }

        private[this] val fb = {
          var contents = List.empty[scala.swing.Component]

          def prepend(c: scala.swing.Component): Unit = {
            if (contents.nonEmpty) {
              contents ::= Swing.HStrut(4)
            }
            contents ::= c
          }

          if (sampleRateVisible   ) {
            prepend(new scala.swing.Label("Hz"))
            contents ::= Swing.HStrut(2)
            contents ::= sampleRateComboBox
          }
          if (sampleFormatVisible )   prepend(sampleFormatComboBox)
          if (fileTypeVisible     )   prepend(fileTypeComboBox    )

          val res = new scala.swing.FlowPanel(scala.swing.FlowPanel.Alignment.Leading)(contents: _*)
          res.vGap  = 0
          res.hGap  = 0
          res
        }

        override lazy val peer: javax.swing.JPanel = {
          val p = new javax.swing.JPanel with SuperMixin {
            override def getBaseline(width: Int, height: Int): Int = {
              if (!pathFieldVisible) super.getBaseline(width, height) else {
                val pfj = pathField.peer
                val d   = pfj.getPreferredSize
                val res = pfj.getBaseline(d.width, d.height)
                res + pfj.getY
              }
            }
          }
          val l = new javax.swing.BoxLayout(p, Orientation.Vertical.id)
          p.setLayout(l)
          p
        }

        if (pathFieldVisible) {
          updateFormat()
          pathField.listenTo(pathField)
          listenToPath()
          contents += pathField
        }
        if (sampleRateVisible || sampleFormatVisible || fileTypeVisible) {
          contents += fb
        }
      }

      component = c
    }
    super.initComponent()
  }
}