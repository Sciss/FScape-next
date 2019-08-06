## 03-Aug-2019

We have `Runner` but that does not give us access to the outputs. Without inventing the 
wheel again, we should use outputs and `GenView` to obtain output results from an `FScape`
instance for further use in a `Widget` / `Ex` program.

While `GenView.apply` takes any `Obj`, we should probably limit the scope for `Ex`, since
we also anyway want to introduce a result type. That would be done through type class
`Obj.Bridge[A]` I guess? Or rather `Aux.FromAny[A]` which is less constrained. (We should
probably get rid of `Obj.Bridge` in its current form).

```
val f     = FScape("key")
val value = f.outputs[Long]("out")
```

Then `genLong` could either have type `Ex[Long]` analogous to `Var[Long]`, or
`Ex[Option[Long]]` to represent possible errors? Like `Var`, the value would be
updated when the rendering has run. The question is how the rendering is triggered.
Do we require explicit `run`, do we take advantage of the automatic caching?
Certainly we should not start the rendering automatically when the graph is
expanded, but we need an explicit trigger.

```
val f     = FScape("key")
val value = f.outputs[Long]("out")
f.run
f.completed ---> PrintLn(value.toStr)
```

Technically, the outputs must be instances of `Control` because they must
be eagerly created and registered with the fscape reference, since `GenView`
instances must be created for them only upon running the rendering, and
they must be _recreated_ again and again, because a single `GenView` completes
only once.

```
object FScape {
  def apply(key: String): FScape = ???

  trait Output[A] extends Ex[A] with Control
}
trait FScape extends Runner {
  def output[A: FromAny](key: String): FScape.Output[A]
}
```

We need

- a simple way to determine whether a runner had an error
- make sure that the outputs are all completed before the runner `state` is updated
- a runner for `Widget` itself, or better for `Control` so we can be headless

----

The implicit start of the runner through `GenView.apply` is unfortunate. If would be better if we could
ensure that output rendering is active (is it always?) upon enforced `run`, and then use the `Runner.peer` of
type `ViewBase` in the `IControl` expanded objects from `.output` to monitor state and update it when the
runner completes. `FScape.Rendering` has `def outputResult(view: OutputGenView[S]): Option[Try[Obj[S]]]`, so
we could use that perhaps. The implementation uses `view.output` only, so we might be able to avoid having to
create an actual `OutputGenView` if we adapt the API. We then still need to get from `Runner` to `Rendering`,
perhaps by defining a public `FScapeRunner <: Runner`.

Looking into the runner implementation:

```
def stop()(implicit tx: S#Tx): Unit = {
  renderRef.swap(None).foreach(_.dispose())
  state = Runner.Stopped
}
```

and the observer, we might use this point for scanning results, before disposal? Like
`outputResults: Map[String, Try[Obj[S]]]` -- ? Alternatively, and perhaps better, we might delay the disposal of
the rendering instance and introduce an additional `stop` method (so we do not have to call `dispose`). Then the
individual outputs could still call `rendering.flatMap(_.outputResult)` or something like that.

----

`OutputImpl` is assumed in `RenderingImpl`, and it is based on the idea that an `Obj` is persisted (`value_=`),
even if it's just a cue to a cache file. For 'Ex', it would be great if that persistence was optional, since we
may not want to store the value, just pass it back into the 'Ex' program. Here is what the implementation of
and output-reference looks like:

```
def updateValue(in: DataInput)(implicit tx: S#Tx): scala.Unit = {
  val value     = reader.readOutput[S](in)
  val output    = outputH()
  output.value_=(Some(value))
}
```

This is all a bit awkward from the rendering perspective, because it relies on this mutable update:

```
val in = DataInput(cv.data(key))
outRef.updateValue(in)
oi.value  // !
```

A clever work-around that does not require the API to change significantly, would be to add a method like
`decodeValue` to the reference, which directly returns the `Obj[S]`, _not setting it_ on the output object
(it would just keep reporting `None` for its "persisted" value). Then of course, for 'Ex' it would be even
greater if we _could_ still use the caching and persisting version if we needed it. How would a user distinguish
them? Perhaps:

```
trait FScape extends Runner {
  def output[A: FromAny](key: String, cache: Boolean = false): FScape.Output[A]
}
```

(__Note:__ this doesn't make sense. Caching must be enabled or disabled for the entire `FScape` reference)

Two things:

- there is still an `Obj[S]` which thus must be persisted in `S`. To solve this, we must introduce a notion
  such as `Obj.Bridge`, together with `FromAny`.
- I need to look up how the cache invalidation works! So `MkAudioCue`, if the `FScape.Output` actually stores
  an `AudioCue.Obj`, how is that purged when the cache is purged? I don't remember.
  
Let's walk through the "completion" of a `MkAudioCue`:

- we obtain an output reference: `ub.requestOutput(this).getOrElse(???)`
- the graph elements implements `readOutput` by reading a plain value and wrapping it in `newConst`,
  so that seems an easy thing to skip!
- make-stream then evokes `ref.createCacheFile()`, passing that into the stream logic.
- when the stage logic is stopped, it runs `ref.complete`, with a writer for the flat audio cue value.
- the UGen graph builder collects the output-references, as a list of extended structures `OutputResult`.
  This is taken by the rendering implementation.
- What is actually cached are not individual files or outputs, but _all resources_ (value) belonging to one
  _program structure_ (key).
- the rendering implementation _takes_ a `Future[CacheValue]` that was acquired from the cache, if `useCache`
  (currently `true` whenever there are outputs.)
- when the future completes (the stream has terminated), `completeWith` is called on the rendering. For all outputs,
  `updateValue` is called. The implicit assumption is that the cache value has been memorised in the cache system,
  because outputs were detected. Thus the rendering's `dispose` just releases the cache if `useCache` was `true`.
  
The `GenView.Factory` ties a use count of renderings to the fscape object, ensuring that as long as not all related
`GenView` instances (output views) are disposed, the implicitly created rendering is also valid and holding the cache
value. As there are no ways the user can find the internal `Rendering`, they can also not call `outputResult` at a
"bad moment", where the `Obj[S]` holds an audio cue whose file has actually been purged.

So this is all very clever. The best approach may be to introduce simply a second version of the rendering
implementation which never persists to disk. Then its the user's responsibility to ensure the validity of external
resources (`MkAudioCue`), in the sense that the contract is that the value remains valid until either

- the runner is restarted
- the expanded graph is disposed (controls are disposed)
- note that currently, runners are automatically disposed with the widget (so this is "good")
- even when enabling caching, the same contract holds (we close the widget, the resources are released
  and potentially purged); so cache yes/no is simply a question of performance not of categorical operation.

Also note, dead API:

- `Rendering.withState` is never used, and neither is trait `WithState` and its method `cacheResult`.
- __correction:__ this is API for SysSon (matrix reader), it is used

