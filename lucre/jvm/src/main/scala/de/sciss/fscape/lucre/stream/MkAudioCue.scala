/*
 *  MkAudioCue.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.lucre.stream

import akka.stream.{Attributes, UniformFanInShape}
import de.sciss.audiofile.AudioFileSpec
import de.sciss.fscape.Util
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeImpl}
import de.sciss.fscape.stream.{BufD, BufL, Builder, Control, InD, Layer, OutD, OutL, AudioFileOut => _AudioFileOut}
import de.sciss.lucre.Artifact
import de.sciss.serial.DataOutput
import de.sciss.proc.AudioCue
import de.sciss.proc.FScape.Output

import scala.collection.immutable.{Seq => ISeq}

object MkAudioCue {
  def apply(ref: OutputRef, uri: Artifact.Value, spec: AudioFileSpec, in: ISeq[OutD])(implicit b: Builder): OutL = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val nameL = Util.mkLogicName(name, uri)
    val sink  = new Stage(layer = b.layer, ref = ref, uri = uri, spec = spec, nameL = nameL)
    val stage = b.add(sink)
    (in zip stage.inlets).foreach { case (output, input) =>
      b.connect(output, input)
    }
    stage.out
  }

  private final val name = "MkAudioCue"

  private type Shp = UniformFanInShape[BufD, BufL]

  private final class Stage(layer: Layer, ref: OutputRef, uri: Artifact.Value, spec: AudioFileSpec, nameL: String)
                           (implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shp](nameL) {

    val shape: Shape = UniformFanInShape[BufD, BufL](
      OutL(s"$name.out"),
      Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch")): _*
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new Logic(nameL, shape, layer, ref, uri, spec)
  }

  private final class Logic(name: String, shape: Shp, layer: Layer, ref: OutputRef, protected val uri: Artifact.Value,
                            protected val spec: AudioFileSpec)
                           (implicit ctrl: Control)
    extends _AudioFileOut.AbstractLogic(name, layer, shape) {

    protected override def launch(): Unit = {
      super.launch()
      onPull()  // needed for asynchronous logic
    }

    override protected def stopped(): Unit = {
      super.stopped()
      if (isSuccess) ref.complete(new Output.Writer {
        override val outputValue: AudioCue = {
          val spec1 = spec.copy(numFrames = framesWritten)
          AudioCue(uri, spec1, 0L, 1.0)
        }

        def write(out: DataOutput): Unit =
          AudioCue.format.write(outputValue, out)
      })
    }
  }
}