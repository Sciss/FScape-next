/*
 *  ImageFileOutExpandedImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre.swing.graph.impl

import java.awt.Color
import java.awt.geom.{AffineTransform, Path2D}
import java.net.URI

import de.sciss.audiowidgets.ShapeIcon
import de.sciss.desktop.{FileDialog, TextFieldWithPaint}
import de.sciss.file._
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.graph.ImageFile.SampleFormat
import de.sciss.fscape.lucre.graph.{ImageFileOut => UImageFileOut}
import de.sciss.lucre.Txn
import de.sciss.lucre.expr.Context
import de.sciss.lucre.expr.graph.Ex
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.graph.{ImageFileIn, ImageFileOut, PathField}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{Shapes, View}
import de.sciss.swingplus.{ComboBox, ListView, Spinner}
import de.sciss.{audiowidgets, desktop}
import javax.swing.{JList, ListCellRenderer, SpinnerNumberModel}

import scala.swing.Reactions.Reaction
import scala.swing.event.{SelectionChanged, ValueChanged}
import scala.swing.{Alignment, Label, Orientation, SequentialContainer, Swing}
import scala.util.Try

final class ImageFileOutExpandedImpl[T <: Txn[T]](protected val peer: ImageFileOut)
  extends View[T] with ComponentHolder[ImageFileOut.Peer] with ComponentExpandedImpl[T] {

  type C = ImageFileOut.Peer

  override def initComponent()(implicit tx: T, ctx: Context[T]): this.type = {
    val pathOpt   = ctx.getProperty[Ex[URI    ]](peer, PathField   .keyValue       ).map(_.expand[T].value)
    val titleOpt  = ctx.getProperty[Ex[String ]](peer, PathField   .keyTitle       ).map(_.expand[T].value)
    val fileTpeIdx= ctx.getProperty[Ex[Int    ]](peer, ImageFileOut.keyFileType    ).fold(ImageFileOut.defaultFileType     )(_.expand[T].value)
    val smpFmtIdx = ctx.getProperty[Ex[Int    ]](peer, ImageFileOut.keySampleFormat).fold(ImageFileOut.defaultSampleFormat )(_.expand[T].value)
    val quality   = ctx.getProperty[Ex[Int    ]](peer, ImageFileOut.keyQuality     ).fold(ImageFileOut.defaultQuality      )(_.expand[T].value)

    def getBoolean(key: String, default: => Boolean): Boolean =
      ctx.getProperty[Ex[Boolean]](peer, key).fold(default)(_.expand[T].value)

    val pathFieldVisible    = getBoolean(ImageFileOut.keyPathFieldVisible   , ImageFileOut.defaultPathFieldVisible    )
    val fileTypeVisible     = getBoolean(ImageFileOut.keyFileTypeVisible    , ImageFileOut.defaultFileTypeVisible     )
    val sampleFormatVisible = getBoolean(ImageFileOut.keySampleFormatVisible, ImageFileOut.defaultSampleFormatVisible )
    val qualityVisible      = getBoolean(ImageFileOut.keyQualityVisible     , ImageFileOut.defaultQualityVisible      )

    deferTx {
      val c: C = new ImageFileOut.Peer with SequentialContainer.Wrapper  { panel =>
        override def enabled_=(b: Boolean): Unit = {
          super.enabled_=(b)
          if (pathFieldVisible    ) pathField           .enabled = b
          if (fileTypeVisible     ) fileTypeComboBox    .enabled = b
          if (sampleFormatVisible ) sampleFormatComboBox.enabled = b
          updateQualityStatus()
        }

        def updatePathOverlay(): Unit =
          pathField.paint = if (pathField.valueOption.exists(_.exists())) {
            Some(TextFieldWithPaint.BlueOverlay)
          } else {
            None
          }

        lazy val pathField: desktop.PathField = {
          val res = new desktop.PathField
          res.mode = FileDialog.Save
          pathOpt .foreach { uri =>
            val fileOpt     = Try(new File(uri)).toOption
            res.valueOption = fileOpt
          }
          titleOpt.foreach(res.title = _)
          res
        }

        val pathReaction: Reaction = {
          case ValueChanged(_) => updatePathOverlay()  // XXX TODO --- refinement: adjust fileType selection
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

        lazy val fileTypeComboBox: ComboBox[ImageFile.Type] = {
          val items = List.tabulate(UImageFileOut.maxFileTypeId + 1)(UImageFileOut.fileType)
          val res = mkCombo(items)(_.name)
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
                    updatePathOverlay()
                    listenToPath()
                  }
                }
              }

              updateQualityStatus()
          }
          res
        }

        def updateQualityStatus(): Unit = if (qualityVisible) {
          val isLossy = Option(fileTypeComboBox.selection.item).exists(_.isLossy)
          val b       = isLossy && panel.enabled
          qualityField.enabled = b
          lbQuality   .enabled = b
        }

        if (fileTypeVisible) updateQualityStatus()

        lazy val sampleFormatComboBox: ComboBox[SampleFormat] = {
          val items = List.tabulate(UImageFileOut.maxSampleFormatId + 1)(UImageFileOut.sampleFormat)
          val res = mkCombo(items)(ImageFileIn.formatToString)
          res.selection.index = smpFmtIdx
          res
        }

        lazy val qualityField: Spinner = {
          val m     = new SpinnerNumberModel(0, 0, 100, 1)
          val res   = new Spinner(m)
          res.value = quality
          res
        }

        private[this] lazy val lbQuality: Label = {
          val shape   = new Path2D.Float(Path2D.WIND_NON_ZERO)
          Shapes.Stars(shape)
          val extent  = 18
          val scale   = extent/32.0
          val at      = AffineTransform.getScaleInstance(scale, scale)
          shape.transform(at)
          val isDark  = audiowidgets.Util.isDarkSkin
          val colrFg  = if (isDark) Color.lightGray else Color.black
          val colrBg  = if (isDark) Color.black     else Color.lightGray
          val colrFgD = new Color(colrFg.getRGB & 0x4FFFFFFF, true)
          val colrBgD = new Color(colrBg.getRGB & 0x4FFFFFFF, true)
          val icnNorm = new ShapeIcon(shape, colrFg , colrBg , extent, extent)
          val icnDis  = new ShapeIcon(shape, colrFgD, colrBgD, extent, extent)

          val res = new Label(null, icnNorm, Alignment.Leading)
          res.disabledIcon = icnDis
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

          if (qualityVisible      ) {
            val tt = "Quality"
            lbQuality   .tooltip  = tt
            qualityField.tooltip  = tt
            prepend(lbQuality)
            contents ::= Swing.HStrut(2)
            contents ::= qualityField
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
          updatePathOverlay()
          pathField.listenTo(pathField)
          listenToPath()
          contents += pathField
        }
        if (qualityVisible || sampleFormatVisible || fileTypeVisible) {
          contents += fb
        }
      }

      component = c
    }
    super.initComponent()
  }
}