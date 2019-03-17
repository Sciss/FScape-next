/*
 *  MkAudioCue.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.lucre.stream

import akka.stream.{Attributes, UniformFanInShape}
import de.sciss.file._
import de.sciss.fscape.lucre.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.stream.impl.{BlockingGraphStage, NodeImpl}
import de.sciss.fscape.stream.{BufD, BufL, Builder, Control, InD, Layer, OutD, OutL, AudioFileOut => _AudioFileOut}
import de.sciss.serial.DataOutput
import de.sciss.synth.io
import de.sciss.synth.io.AudioFileSpec
import de.sciss.synth.proc.AudioCue

import scala.collection.immutable.{Seq => ISeq}

object MkAudioCue {
  def apply(ref: OutputRef, file: File, spec: AudioFileSpec, in: ISeq[OutD])(implicit b: Builder): OutL = {
    require (spec.numChannels == in.size, s"Channel mismatch (spec has ${spec.numChannels}, in has ${in.size})")
    val sink = new Stage(layer = b.layer, ref = ref, f = file, spec = spec)
    val stage = b.add(sink)
    (in zip stage.inlets).foreach { case (output, input) =>
      b.connect(output, input)
    }
    stage.out
  }

  private final val name = "MkAudioCue"

  private type Shape = UniformFanInShape[BufD, BufL]

  private final class Stage(layer: Layer, ref: OutputRef, f: File, spec: io.AudioFileSpec)
                           (implicit protected val ctrl: Control)
    extends BlockingGraphStage[Shape](s"$name(${f.name})") {

    val shape: Shape = UniformFanInShape[BufD, BufL](
      OutL(s"$name.out"),
      Vector.tabulate(spec.numChannels)(ch => InD(s"$name.in$ch")): _*
    )

    def createLogic(attr: Attributes) = new Logic(shape, layer, ref, f, spec)
  }

  private final class Logic(shape: Shape, layer: Layer, ref: OutputRef, protected val file: File,
                            protected val spec: io.AudioFileSpec)
                           (implicit ctrl: Control)
    extends NodeImpl(s"$name(${file.name})", layer, shape) with _AudioFileOut.AbstractLogic {

    override protected def stopped(): Unit = {
      super.stopped()
      if (isSuccess) ref.complete(new Output.Writer {
        def write(out: DataOutput): Unit = {
          val spec1 = spec.copy(numFrames = framesWritten)
          val flat  = AudioCue(file, spec1, 0L, 1.0)
          AudioCue.serializer.write(flat, out)
        }
      })
    }
  }
}