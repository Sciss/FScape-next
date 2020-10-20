package de.sciss.fscape.stream.impl

import org.scalajs.dom.raw.AudioNode
import org.scalajs.dom.{AudioBuffer, Event}

import scala.scalajs.js

/** The `ScriptProcessorNode` interface allows the generation, processing, or analyzing
  * of audio using JavaScript. It is an `AudioNode` audio-processing module that is
  * linked to two buffers, one containing the input audio data, one containing the
  * processed output audio data. An event, implementing the `AudioProcessingEvent`
  * interface, is sent to the object each time the input buffer contains new data,
  * and the event handler terminates when it has filled the output buffer with data.
  */
@js.native
trait ScriptProcessorNode extends AudioNode {
  /** Returns an integer representing both the input and output buffer size.
    * Its value can be a power of 2 value in the range 256--16384.
    */
  def bufferSize: Int = js.native

  /** Represents the `EventHandler` to be called. */
  var onaudioprocess: js.Function1[AudioProcessingEvent, Unit] = js.native
}

/** The Web Audio API `AudioProcessingEvent` represents events that occur when a
  * `ScriptProcessorNode` input buffer is ready to be processed.
  */
@js.native
trait AudioProcessingEvent extends Event {
  /** The time when the audio will be played, as defined by the time of
    * `AudioContext.currentTime`
    */
  def playbackTime: Double = js.native

  /** The buffer containing the input audio data to be processed. The number
    * of channels is defined as a parameter, `numberOfInputChannels`, of the factory
    * method `AudioContext.createScriptProcessor()`. Note the the returned `AudioBuffer`
    * is only valid in the scope of the `onaudioprocess` function.
    */
  def inputBuffer: AudioBuffer = js.native

  /** The buffer where the output audio data should be written. The number
    * of channels is defined as a parameter, `numberOfOutputChannels`, of the factory
    * method AudioContext.createScriptProcessor(). Note the the returned AudioBuffer
    * is only valid in the scope of the onaudioprocess function.
    */
  def outputBuffer: AudioBuffer = js.native
}