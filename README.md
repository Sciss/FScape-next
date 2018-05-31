# FScape-next

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Sciss/FScape?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/Sciss/FScape-next.svg?branch=master)](https://travis-ci.org/Sciss/FScape-next)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sciss/fscape_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sciss/fscape_2.11)

## statement

An audio rendering (offline signal processing) library. Although not optimised for it, it can also be used to
process still images and image sequences (aka videos). This project should eventually become the next major version of
[FScape](https://github.com/Sciss/FScape), an experimental music and sound signal processing workbench. As of
this writing, the library is still experimental (although very useful, and I have used it in several projects).
It has basic integration with [Mellite](http://sciss.github.io/Mellite/), but since Mellite does not yet offer
"widget programs", it currently does not replace FScape (version 1) yet.

FScape(-next) is (C)opyright 2001&ndash;2018 by Hanns Holger Rutz. All rights reserved.
This program is free software; you can redistribute it and/or modify it under the terms 
of the [GNU General Public License](http://github.com/Sciss/FScape-next/blob/master/LICENSE) v2+.
To contact the author, send an email to `contact at sciss.de`.

## building

FScape 2 builds with sbt and Scala 2.12, 2.11. It requires Java 8.

## linking

The following dependency is necessary:

    "de.sciss" %% "fscape" % v

The current version `v` is `"2.15.3"`.

The following sub modules are available:

    "de.sciss" %% "fscape-core"  % v  // core library
    "de.sciss" %% "fscape-lucre" % v  // integration with SoundProcesses

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)

## design

The goals of this project are:

- completely separate GUI and processes
- move from Java to Scala
- reformulate modules as a graph of small modular DSP blocks
- DSP blocks are modelled as UGens similar to those in [ScalaCollider](https://github.com/Sciss/ScalaCollider),
  with the crucial difference that in FScape processes run in non-realtime and usually will have a bounded duration
- currently UGens graphs will be translated into an Akka Stream graph, hopefully providing a robust
  streaming API and the possibility to customise parallelism
- provide a simple DSL similar to ScalaCollider for users to write their own DSP graphs
- integrate these graphs into [Mellite](https://github.com/Sciss/Mellite)

FScape (v2) provides a set of unit generators in the package `de.sciss.fscape.graph` which roughly follow the
idea of UGens in ScalaCollider. Or more precisely, the building blocks are of type `GE` or _graph element_.
A graph element can abstract over UGens (introduce pseudo-UGens) and abstract over the number of channels
(perform a late multi-channel-expansion). The inputs of a graph element are usually other graph elements.
Primitive numbers are lifted to graph elements implicitly. Unlike the real-time counterpart ScalaCollider,
graph elements may _last_ for a specific number of frames.

Some have an infinite duration, e.g. generators such as
`SinOsc`, `DC`, or `WhiteNoise`. Others have a finite duration, e.g. `AudioFileIn`. Filters usually end, when their
"hot" input (typically the first input) ends, while they cache the values of auxiliary inputs. For example,
if you write `Sliding(in, 512, 32)` you are creating sliding and overlapping windows over then input `in`.
The window size here is `512` and the stepping size is `32`. Both `512` and `32` are constants that have a duration
of _one_ frame, but `Sliding` keeps processing until the hot input, `in`, terminates. If one wanted to modulate
the window size, a dynamic signal could be used instead of the constant.

## example

The following [program](https://github.com/Sciss/FScape-next/blob/master/core/src/test/scala/de/sciss/fscape/ConstQTest.scala)
reads in a monophonic sound file and outputs a logarithmic gray scale sonogram PNG image:

```scala
import de.sciss.fscape._
import de.sciss.synth.io.AudioFile

val fIn       = new java.io.File("input.aif")
val specIn    = AudioFile.readSpec(fIn)
val fOut      = new java.io.File("sonogram.png")
val fftSize   = 8192
val timeResMS = 4.0   // milliseconds
val winStep   = math.min(fftSize, (timeResMS / 1000 * specIn.sampleRate + 0.5).toInt)
val numWin    = ((specIn.numFrames - fftSize + winStep - 1) / winStep).toInt
val numBands  = 432
val dbMin     = -78.0
val dbMax     = -18.0

val g = Graph {
  import graph._
  val in        = AudioFileIn(file = fIn, numChannels = 1)
  val slid      = Sliding(in, fftSize, winStep)
  val winFun    = GenWindow(size = fftSize, shape = GenWindow.Hann)
  val windowed  = slid * winFun
  val rotWin    = RotateWindow(windowed, size = fftSize, amount = fftSize/2)
  val fft       = Real1FFT(rotWin, size = fftSize)
  val constQ    = ConstQ(fft, fftSize = fftSize, numBands = numBands)
  val norm      = constQ.ampDb.linLin(dbMin * 2, dbMax * 2, 0.0, 1.0).clip()
  val rotImg    = RotateFlipMatrix(norm, rows = numWin, columns = numBands, mode = RotateFlipMatrix.Rot90CCW)
  val specOut   = ImageFile.Spec(width = numWin, height = numBands, numChannels = 1)
  ImageFileOut(file = fOut, spec = specOut, in = rotImg)
}

val ctrl  = stream.Control()

ctrl.run(g)
import ctrl.config.executionContext
ctrl.status.foreach { _ => sys.exit() }
```

The actual DSP program is written inside a `Graph { }` block, which you can think of as a variant of 
ScalaCollider's `SynthGraph`. `AudioFileIn` reads and streams a sound file, `Sliding` produces overlapping windows,
these weighted by a Hann window, then rotated in their phase, then the Fourier transform is taken. The `ConstQ`
element applies filters to the spectrum that give us the squared magnitudes of the logarithmic frequency bands
(we use the defaults of `ConstQ` here). The `norm` variable captures these magnitudes in a useful range for
representation as gray scale pixels. Before the image is written out, we rotate it, so that time corresponds to
the x-Axis and frequencies corresponds to the y-Axis. Note that FScape does not have a dedicated 2-dimensional
image signal type, instead images are transported as 1D signals, scanning from left to right and top to bottom.
You could expand the example to process a stereo sound file by changing to `numChannels = 2`, all other UGens would
automatically multi-channel-expand. In the end you could decide to mix the two channels together
(make sure to clip the sum, otherwise you may see wrapped pixel values):

```
ImageFileOut(file = fOut, spec = specOut, in = (rotImg.out(0) + rotImg.out(1)).min(1.0))
```

Or you could decide that they should indicate the red and green channel, and add an empty blue channel:

```
val specOut = ImageFile.Spec(width = numWin, height = numBands, numChannels = 3)
ImageFileOut(file = fOut, spec = specOut, in = Seq(rotImg.out(0), rotImg.out(1), DC(0.0)))
```

For more examples, browse the 'test' sources. Also see
the [API Documentation](http://sciss.github.io/Mellite/latest/api/de/sciss/fscape/).

## notes

- early blog post: https://sciss.github.io/rethinking-fscape/
- future versions may take some lessons from the [Patterns](https://github.com/Sciss/Patterns) project, namely
  to use typed streams instead of the opaque `GE`. Currently, `GE` can "hide" different element types such as
  `Double`, `Int`, `Long`. This may create confusing behaviour with regard to some arithmetic operators, and also
  it makes it unclear what the underlying type may be. Secondly, the notion of _resettable_ programs is introduced
  by Patterns and may provide a powerful application in FScape, too. Thirdly, we may want to create a bridging
  mechanism between the two, so that one can use patterns for low throughput symbols, take advantage of the powerful
  collections-style operations in Patterns, while using FScape for the actual DSP signals.
- to replace FScape v1, we would probably add an abstraction to Mellite that allows one to write "widget programs"
  which can thus represent the module GUIs of FScape v1.
- the "hot" inlet style can lead **to very confusing behaviour** (see issue #7) of the seemingly
  symmetric `BinaryOp`; notably
  `a + b` may not behave the same as `b + a`, because in the first case the output stream ends when `a` ends, and in the
  second case the output stream ends when `b` ends. So `1 + SinOsc(...)` creates a stream with the length of
  one sample frame, while `SinOsc(...) + 1` creates an infinite stream. This will be addressed in 
  an upcoming version.
