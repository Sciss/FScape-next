# FScape

- mostly we learned what _not_ to do (threads, GUI, parameters...)
- we had a module-autonomous view: Inputs are pulled, outputs are pushed.

# EisK

- the inversion of control with `producerRender` might make things too complicated from the module point of view
- on the other hand, having clear "phases" would help. Any module could be decomposed into a number of phases each
  of which has a fixed state vector
- a bit like `Actor.become`?
- do we only support a linear sequence of phases?
- it also sounds reasonable to keep anything `Future` or `blocking` out of the picture
- the block buffers are perhaps too rigid. We're offline, so we don't need to imitate UGens

# SoundProcesses

- certainly there is a distinction between a module type/factory, a module setting/instance, and a running module/view
- there will be a translation from `[S]` parameters to view parameters
- basically this should be an entirely independent layer, just like ScalaCollider
- we could allow 'runtime' types like in `AttrMap` with 'runtime-type classes' such as `AuralAttribute.Input`

--------------------

We should make a clear decision regarding `Param`. Intuition says we should avoid having to deal with units
at all inside the module, i.e. window sizes, durations etc. etc. should all be given in sample frames here,
factors and amplitudes all as linear floating point values. Formatting them in a human readable way is the
purpose of the plugin graphical user interface. If we have an operation such as `cue.sampleRate_#`, we
can compose those translations and yield a directly usable `Expr[Int]`.

- Are we focusing on push or pull based models? 
- Do we allow random-access-outputs or only random-access-inputs?
- Should we just switch to double precision or still use 32-bit floats? Speed-wise, they are equivalent on
  modern architectures, while the former has less noise. However, heap buffer sizes will be much larger.
- Do we share I/O buffers between modules?

Traditionally, real-time sound synthesis systems are push based (data-driven). That is, input buffers are
filled, then the UGen is called, then we proceed with the sinks. On the other hand, assuming that we have some 
data-flow graph at some point, it could be more useful to think demand-driven.
This in turn would make the question about random-access-outputs more relevant.

Pull-based makes sense for views, e.g. sonogram overview. But if I apply a filter in EisK, that's clearly
push-based. More so, if output length is not easily pre-determined, which is simple for inputs (always?).

Let's say we wipe a rendered file to minimise back-up space. Then we open the session and have the files
re-rendered. Intuitively, that would also be a bottom-up or pull-based operation. On the other hand, the
infra-structure could easily translate that into a regular top-down process again, perhaps making the
module's own body simpler to design.

A case that might be particularly important is where we only use a portion of an output. If we are to
recreate that from a wiped cache, we definitely want to minimise the effort of rendering. So the module
should provide us a mechanism to translate output span selections to input span selections.

For which modules would it be difficult to make these translations?

- Resample: would have to integrate the frequency function. So depends on whether rate is static or not.
  For simplicity, we could provide two modules, one with static factor, one with modulator.
- Wavelet, Fourier: easy to calculate span, but not to render without rendering the whole output
- Basically all in 'Time Domain': Step Back, Kriechstrom, Seek + Enjoy, Lückenbüßer, Pearson Plotter, Sediment, Murke
- Ichneumon, Rotation
- All that have RNG or IIR: BlunderFinger, ...

(NOT: Similar to a streaming protocol, there could be a notion of "completed output spans".)

## links

- Philipp Haller: "Can we make concurrency in Scala safer?" https://www.youtube.com/watch?v=nwWvPeX6U9w 