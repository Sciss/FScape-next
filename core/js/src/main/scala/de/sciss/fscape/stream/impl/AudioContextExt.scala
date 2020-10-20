package de.sciss.fscape.stream.impl

import org.scalajs.dom.AudioContext

import scala.scalajs.js

/** Includes extensions not present in the original `AudioContext` class. */
@js.native
trait AudioContextExt extends AudioContext {
  /** The `createScriptProcessor()` method of the `AudioContext` interface creates
    * a `ScriptProcessorNode` used for direct audio processing.
    *
    * @param bufferSize             The buffer size in units of sample-frames. If specified,
    *                               the bufferSize must be one of the following values:
    *                               256, 512, 1024, 2048, 4096, 8192, 16384. If it's not
    *                               passed in, or if the value is 0, then the implementation
    *                               will choose the best buffer size for the given environment,
    *                               which will be a constant power of 2 throughout the
    *                               lifetime of the node.
    *
    *                               This value controls how frequently the audioprocess event
    *                               is dispatched and how many sample-frames need to be processed
    *                               each call. Lower values for `bufferSize` will result in a
    *                               lower (better) latency. Higher values will be necessary to
    *                               avoid audio breakup and glitches. It is recommended for
    *                               authors to not specify this buffer size and allow the
    *                               implementation to pick a good buffer size to balance between
    *                               latency and audio quality.
    *
    * @param numberOfInputChannels  Integer specifying the number of channels for this node's
    *                               input, defaults to 2. Values of up to 32 are supported.
    *
    * @param numberOfOutputChannels Integer specifying the number of channels for this node's
    *                               output, defaults to 2. Values of up to 32 are supported.
    */
  def createScriptProcessor(bufferSize            : Int = 0,
                            numberOfInputChannels : Int = 2,
                            numberOfOutputChannels: Int = 2): ScriptProcessorNode = js.native
}