/*
 *  ImageFileOut.scala
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

package de.sciss.fscape.lucre.stream

import java.net.URI

import akka.stream.{Attributes, Outlet}
import de.sciss.fscape.Util
import de.sciss.fscape.graph.ImageFile
import de.sciss.fscape.lucre.stream.impl.ImageFileOutReadsSpec
import de.sciss.fscape.stream.impl.Handlers.{InDMain, InIAux}
import de.sciss.fscape.stream.impl.shapes.In5UniformSinkShape
import de.sciss.fscape.stream.impl.{BlockingGraphStage, Handlers, ImageFileSingleOutImpl, NodeImpl}
import de.sciss.fscape.stream.{BufD, BufI, Builder, Control, InD, InI, Layer, OutD, OutI}

import scala.collection.immutable.{Seq => ISeq}

object ImageFileOut {
  def apply(uri: URI, width: OutI, height: OutI, fileType: OutI, sampleFormat: OutI, quality: OutI, in: ISeq[OutD])
           (implicit b: Builder): Unit = {
    val nameL   = Util.mkLogicName(name, uri)
    val stage0  = new Stage(layer = b.layer, uri = uri, numChannels = in.size, nameL = nameL)
    val stage   = b.add(stage0)
    b.connect(width       , stage.in0)
    b.connect(height      , stage.in1)
    b.connect(fileType    , stage.in2)
    b.connect(sampleFormat, stage.in3)
    b.connect(quality     , stage.in4)
    (in zip stage.inlets5).foreach { case (output, input) =>
      b.connect(output, input)
    }
//    stage.out
  }

  private final val name = "ImageFileOut"

  private type Shp = In5UniformSinkShape[BufI, BufI, BufI, BufI, BufI, BufD]

  private final class Stage(layer: Layer, uri: URI, numChannels: Int, nameL: String)
                           (implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shp](nameL) {

    val shape: Shape = In5UniformSinkShape(
      InI (s"$name.width"       ),
      InI (s"$name.height"      ),
      InI (s"$name.fileType"    ),
      InI (s"$name.sampleFormat"),
      InI (s"$name.quality"     ),
      Vector.tabulate(numChannels)(ch => InD(s"$name.in$ch"))
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] =
      new Logic(nameL, shape, layer, uri, numChannels = numChannels)
  }

  private final class Logic(name: String, shape: Shp, layer: Layer, uri: URI, protected val numChannels: Int)
                           (implicit ctrl: Control)
    extends Handlers(name, layer, shape)
      with ImageFileSingleOutImpl[Shp] with ImageFileOutReadsSpec[Shp] { self =>

    protected val hImg: Array[InDMain] = shape.inlets5.iterator.map(InDMain(this, _)).toArray

    protected val hWidth        : InIAux = InIAux(this, shape.in0)()
    protected val hHeight       : InIAux = InIAux(this, shape.in1)()
    protected val hFileType     : InIAux = InIAux(this, shape.in2)()
    protected val hSampleFormat : InIAux = InIAux(this, shape.in3)()
    protected val hQuality      : InIAux = InIAux(this, shape.in4)()

    protected def fileOrTemplate: URI = uri

    override protected def initSpec(spec: ImageFile.Spec): Unit = {
      super.initSpec(spec)
      openImage(fileOrTemplate)
    }

    override protected def onDone(outlet: Outlet[_]): Unit =
      super.onDone(outlet)
  }
}