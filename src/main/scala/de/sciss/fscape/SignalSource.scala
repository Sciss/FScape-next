package de.sciss.fscape

import scalaxy.streams.optimize

object SignalSource {
  trait Rewindable extends SignalSource {
    /** Resets the read position to zero. */
    def rewind(): Unit
  }

  trait RandomAccess extends Rewindable {
    // def numFrames: Long

    /** Sets the read position to an arbitrary frame.
      * Throws an exception if one tries to seek beyond the end of the source.
      */
    def seek(frame: Long): Unit
  }

  def Constant(d: Num): RandomAccess = new Constant(d)

  private final class Constant(d: Num) extends RandomAccess {
    // private[this] var _pos = 0L

    def numChannels : Int     = 1
    def sampleRate  : Double  = 0.0
    def numFrames   : Long    = -1L

    def available: Int = Int.MaxValue

    def position: Long = 0L // _pos
    def rewind(): Unit = () // _pos = 0L

    def seek(frame: Long): Unit = () // _pos = frame

    def read(data: Frames, off: Int, len: Int): Unit = optimize {
      val stop = off + len
      data.foreach { ch =>
        // java.util.Arrays.fill(ch, off, stop, d) -- same as below plus a stupid range check
        for (i <- off until stop) ch(i) = d
      }
    }

    def rewindable  (): Rewindable    = this
    def randomAccess(): RandomAccess  = this
  }
}
trait SignalSource {
  import SignalSource.{Rewindable, RandomAccess}

  def numChannels: Int

  /** Samples per second or zero if undefined. */
  def sampleRate: Double

  /** The number of frames, if known, or `-1L` if unknown. */
  def numFrames: Long

  /** The number of frames available for read, or `0` if reached the end of the stream. */
  def available: Int

  /** A synchronous (possibly blocking) read. */
  def read(data: Frames, off: Int, len: Int): Unit

  def position: Long

  /** Lists this source into a source that can be rewound.
    * This may be a cheap or an expensive operation
    * (e.g. requiring the creation of an intermediary buffer.
    */
  def rewindable(): Rewindable

  /** Lists this source into a source that has random access (`seek`).
    * This may be a cheap or an expensive operation
    * (e.g. requiring the creation of an intermediary buffer.
    */
  def randomAccess(): RandomAccess
}

