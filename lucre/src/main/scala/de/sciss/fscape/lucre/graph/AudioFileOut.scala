/*
 *  AudioFileOut.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package graph

import de.sciss.file.File
import de.sciss.fscape
import de.sciss.fscape.graph.Constant
import de.sciss.fscape.lucre.UGenGraphBuilder.Input
import de.sciss.synth.io.{AudioFileSpec, AudioFileType, SampleFormat}
import de.sciss.synth.proc.AudioCue

import scala.annotation.switch

object AudioFileOut {
  /** Converts an audio file type to a unique id that can be parsed by the UGen. */
  def id(in: AudioFileType): Int = in match {
    case AudioFileType.AIFF    => 0
    case AudioFileType.Wave    => 1
    case AudioFileType.Wave64  => 2
    case AudioFileType.IRCAM   => 3
    case AudioFileType.NeXT    => 4
    case other => sys.error(s"Unexpected audio file type $other")
  }

  /** Converts a sample forat to a unique id that can be parsed by the UGen. */
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
    case other => sys.error(s"Unexpected audio file type id $other")
  }

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
  *                     is `0` (AIFF).
  *                     Must be resolvable at init time.
  * @param sampleFormat a sample format id as given by `AudioFileOut.id()`. The default
  *                     is `2` (32-bit Float).
  *                     Must be resolvable at init time.
  * @param sampleRate   the nominal sample-rate of the file. The default is `44100`.
  *                     Must be resolvable at init time.
  */
final case class AudioFileOut(key: String, in: GE, fileType: GE = 0, sampleFormat: GE = 2, sampleRate: GE = 44100.0)
  extends GE.Lazy {

  import UGenGraphBuilder.{canResolve, resolve}

  private def fail(arg: String, detail: String): Nothing =
    throw new IllegalArgumentException(s"$productPrefix.$arg cannot be resolved at initialization time: $detail")

  canResolve(fileType    ).left.foreach(fail("fileType"    , _))
  canResolve(sampleFormat).left.foreach(fail("sampleFormat", _))
  canResolve(sampleRate  ).left.foreach(fail("sampleRate"  , _))

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val ub = UGenGraphBuilder.get(b)
    val (f, numChannels, specOpt) = ub.requestInput(Input.Attribute(key)).peer.fold[(File, Int, Option[AudioFileSpec])] {
      sys.error(s"Missing Attribute $key")
    } {
      case a: AudioCue =>
        (a.artifact, a.numChannels, Some(a.spec))

      case f: File =>
        val inExp       = in.expand(b)
        val numChannels = inExp.outputs.size
        (f, numChannels, None)

      case other => sys.error(s"$this - requires AudioCue or Artifact value, found $other")
    }
    val spec = specOpt.getOrElse {
      val fileTypeId  = resolve(fileType    , ub).fold[Constant](fail("fileType"    , _), identity).intValue
      val sampleFmtId = resolve(sampleFormat, ub).fold[Constant](fail("sampleFormat", _), identity).intValue
      val sampleRateT = resolve(sampleRate  , ub).fold[Constant](fail("sampleRate"  , _), identity).doubleValue
      val fileTypeT   = AudioFileOut.fileType    (fileTypeId )
      val sampleFmtT  = AudioFileOut.sampleFormat(sampleFmtId)
      AudioFileSpec(fileTypeT, sampleFmtT, numChannels = numChannels, sampleRate = sampleRateT)
    }

    fscape.graph.AudioFileOut(file = f, spec = spec, in = in)
  }
}