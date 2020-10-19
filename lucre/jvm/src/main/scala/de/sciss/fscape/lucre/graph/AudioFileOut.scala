/*
 *  AudioFileOut.scala
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

package de.sciss.fscape
package lucre
package graph

import de.sciss.file.File
import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.lucre.UGenGraphBuilder.{Input, MissingIn}
import de.sciss.fscape.stream.{StreamIn, StreamOut, Builder}
import de.sciss.synth.io.{AudioFileSpec, AudioFileType, SampleFormat}
import de.sciss.synth.proc.AudioCue

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.util.{Failure, Success, Try}

object AudioFileOut {
  /** Converts an audio file type to a unique id that can be parsed by the UGen. */
  def id(in: AudioFileType): Int = in match {
    case AudioFileType.AIFF    => 0
    case AudioFileType.Wave    => 1
    case AudioFileType.Wave64  => 2
    case AudioFileType.IRCAM   => 3
    case AudioFileType.NeXT    => 4
    case AudioFileType.Raw     => 5
    case other => sys.error(s"Unexpected audio file type $other")
  }

  /** Converts a sample format to a unique id that can be parsed by the UGen. */
  def id(in: SampleFormat): Int = in match {
    case SampleFormat.Int16    => 0
    case SampleFormat.Int24    => 1
    case SampleFormat.Float    => 2
    case SampleFormat.Int32    => 3
    case SampleFormat.Double   => 4
    case SampleFormat.UInt8    => 5
    case SampleFormat.Int8     => 6
    case other => sys.error(s"Unexpected sample format $other")
  }

  /** Recovers an audio file type from an id. Throws an exception if the id is invalid. */
  def fileType(id: Int): AudioFileType = (id: @switch) match {
    case 0 => AudioFileType.AIFF
    case 1 => AudioFileType.Wave
    case 2 => AudioFileType.Wave64
    case 3 => AudioFileType.IRCAM
    case 4 => AudioFileType.NeXT
    case 5 => AudioFileType.Raw
    case other => sys.error(s"Unexpected audio file type id $other")
  }

  def maxFileTypeId: Int = 5

  /** Recovers a sample format from an id. Throws an exception if the id is invalid. */
  def sampleFormat(id: Int): SampleFormat = (id: @switch) match {
    case 0 => SampleFormat.Int16
    case 1 => SampleFormat.Int24
    case 2 => SampleFormat.Float
    case 3 => SampleFormat.Int32
    case 4 => SampleFormat.Double
    case 5 => SampleFormat.UInt8
    case 6 => SampleFormat.Int8
    case other => sys.error(s"Unexpected sample format id $other")
  }

  def maxSampleFormatId: Int = 6

  final case class WithFile(fileTr: Try[File], in: GE, fileType: GE,
                            sampleFormat: GE, sampleRate: GE)
    extends UGenSource.SingleOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      unwrap(this, sampleRate.expand +: sampleFormat.expand +: fileType.expand +: in.expand.outputs)

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike = {
      val adjuncts = fileTr.toOption.fold(List.empty[Adjunct])(Adjunct.FileOut(_) :: Nil)  // Try#fold requires Scala 2.12
      UGen.SingleOut(this, args, adjuncts = adjuncts, isIndividual = true, hasSideEffect = true)
    }

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: Builder): StreamOut = {
      val sampleRate +: sampleFormat +: fileType +: in = args
      lucre.stream.AudioFileOut(fileTr = fileTr, in = in.map(_.toDouble),
        fileType = fileType.toInt, sampleFormat = sampleFormat.toInt,
        sampleRate = sampleRate.toDouble)
    }

    override def productPrefix: String = s"AudioFileOut$$WithFile"
  }
}
/** A graph element that creates a UGen writing to a file
  * designated by an object attribute with a given `key` and the
  * value being an `Artifact`.
  *
  * If the given file-type `GE` is `-1`, the type is determined by this
  * artifact. For example, if the artifact's path ends in `".aif"`, the AIFF
  * format will used, if the path ends in `".w64"`, then Wave64 will be used.
  * The default is AIFF.
  *
  * If an `AudioCue` is found at the key, its spec will override file type,
  * sample-format and rate.
  *
  * @param key          the key into the enclosing object's attribute map,
  *                     pointing to an `Artifact` or `AudioCue`
  * @param in           the signal to write
  * @param fileType     a file type id as given by `AudioFileOut.id()`. The default
  *                     is `-1` (auto-detect).
  * @param sampleFormat a sample format id as given by `AudioFileOut.id()`. The default
  *                     is `2` (32-bit Float).
  * @param sampleRate   the nominal sample-rate of the file. The default is `44100`.
  */
final case class AudioFileOut(key: String, in: GE, fileType: GE = -1, sampleFormat: GE = 2, sampleRate: GE = 44100.0)
  extends GE.Lazy {

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val ub = UGenGraphBuilder.get(b)
    val (fileTr, specOpt) = {
      ub.requestInput(Input.Attribute(key)).peer.fold[(Try[File], Option[AudioFileSpec])] {
        Failure(MissingIn(s"Missing Attribute $key")) -> None
      } {
        case a: AudioCue =>
          val spec = a.spec
          Success(a.artifact) -> Some(spec)

        case f: File =>
          Success(f) -> None

        case other =>
          Failure(new IllegalArgumentException(s"$this - requires AudioCue or Artifact value, found $other")) -> None
      }
    }

    val fileType0     = specOpt.fold(fileType     )(spec => AudioFileOut.id(spec.fileType     ))
    val sampleFormat0 = specOpt.fold(sampleFormat )(spec => AudioFileOut.id(spec.sampleFormat ))
    val sampleRate0   = specOpt.fold(sampleRate   )(spec => spec.sampleRate)
    AudioFileOut.WithFile(fileTr = fileTr, in = in, fileType = fileType0,
      sampleFormat = sampleFormat0, sampleRate = sampleRate0)
  }
}