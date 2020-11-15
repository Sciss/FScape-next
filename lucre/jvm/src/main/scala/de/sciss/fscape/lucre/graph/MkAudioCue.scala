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

package de.sciss.fscape
package lucre
package graph

import de.sciss.fscape.UGen.Adjunct
import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.graph.Constant
import de.sciss.synth.proc.FScape.Output
import de.sciss.fscape.lucre.UGenGraphBuilder.OutputRef
import de.sciss.fscape.stream.{StreamIn, StreamOut, Builder => SBuilder}
import de.sciss.lucre.{Obj, Txn, Workspace}
import de.sciss.serial.DataInput
import de.sciss.audiofile.{AudioFileSpec, AudioFileType, SampleFormat}
import de.sciss.synth.proc.AudioCue

import scala.collection.immutable.{IndexedSeq => Vec}

object MkAudioCue {
  /** Converts an audio file type to a unique id that can be parsed by the UGen. */
  def id(in: AudioFileType): Int = AudioFileOut.id(in)

  /** Converts a sample format to a unique id that can be parsed by the UGen. */
  def id(in: SampleFormat): Int = AudioFileOut.id(in)

  /** Recovers an audio file type from an id. Throws an exception if the id is invalid. */
  def fileType(id: Int): AudioFileType = AudioFileOut.fileType(id)

  /** Recovers a sample format from an id. Throws an exception if the id is invalid. */
  def sampleFormat(id: Int): SampleFormat = AudioFileOut.sampleFormat(id)

  // ----

  final case class WithRef(spec: AudioFileSpec, in: GE, ref: OutputRef) extends UGenSource.SingleOut {

    protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
      unwrap(this, Vector(in.expand))

    protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
      UGen.SingleOut(this, args, adjuncts = Adjunct.String(ref.key) :: Nil, hasSideEffect = true)

    private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: SBuilder): StreamOut = {
      val in    = args.map(_.toDouble)
      val file  = ref.createCacheFile()
      lucre.stream.MkAudioCue(uri = file, spec = spec, in = in, ref = ref)
    }

    override def productPrefix: String = s"MkAudioCue$$WithRef"
  }
}
/** A graph element that creates a UGen writing to an audio file
  * designated by an `FScape.Output` with a given `key` and the
  * value being an `AudioCue`.
  *
  * @param key          the key into the enclosing object's outputs map,
  *                     producing an `AudioCue`
  * @param in           the signal to write
  * @param fileType     a file type id as given by `MkAudioCue.id()`. The default
  *                     is `0` (AIFF).
  *                     Must be resolvable at init time.
  * @param sampleFormat a sample format id as given by `MkAudioCue.id()`. The default
  *                     is `2` (32-bit Float).
  *                     Must be resolvable at init time.
  * @param sampleRate   the nominal sample-rate of the file. The default is `44100`.
  *                     Must be resolvable at init time.
  */
final case class MkAudioCue(key: String, in: GE, fileType: GE = 0, sampleFormat: GE = 2, sampleRate: GE = 44100.0)
  extends GE.Lazy with Output.Reader {

  import UGenGraphBuilder.{canResolve, resolve}

  private def fail(arg: String, detail: String): Nothing =
    throw new IllegalArgumentException(s"$productPrefix.$arg cannot be resolved at initialization time: $detail")

  canResolve(fileType    ).left.foreach(fail("fileType"    , _))
  canResolve(sampleFormat).left.foreach(fail("sampleFormat", _))
  canResolve(sampleRate  ).left.foreach(fail("sampleRate"  , _))

  def tpe: Obj.Type = AudioCue.Obj

  override def readOutputValue(in: DataInput): AudioCue =
    AudioCue.format.read(in)

  def readOutput[T <: Txn[T]](in: DataInput)(implicit tx: T, workspace: Workspace[T]): Obj[T] = {
    val flat = readOutputValue(in)
    AudioCue.Obj.newConst(flat)
  }

  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike = {
    val ub          = UGenGraphBuilder.get(b)
    val refOpt      = ub.requestOutput(this)
    val ref         = refOpt.getOrElse(sys.error(s"Missing output $key"))

    val inExp       = in.expand(b)
    val numChannels = inExp.outputs.size

    val fileTypeId  = resolve(fileType    , ub).fold[Constant](fail("fileType"    , _), identity).intValue
    val sampleFmtId = resolve(sampleFormat, ub).fold[Constant](fail("sampleFormat", _), identity).intValue
    val sampleRateT = resolve(sampleRate  , ub).fold[Constant](fail("sampleRate"  , _), identity).doubleValue
    val fileTypeT   = AudioFileOut.fileType    (fileTypeId )
    val sampleFmtT  = AudioFileOut.sampleFormat(sampleFmtId)
    val spec        = AudioFileSpec(fileTypeT, sampleFmtT, numChannels = numChannels, sampleRate = sampleRateT)

    MkAudioCue.WithRef(spec = spec, in = in, ref = ref)
  }
}