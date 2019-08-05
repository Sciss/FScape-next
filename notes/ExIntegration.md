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
`ouputResults: Map[String, Try[Obj[S]]]` -- ? Alternatively, and perhaps better, we might delay the disposal of
the rendering instance and introduce an additional `stop` method (so we do not have to call `dispose`). Then the
individual outputs could still call `rendering.flatMap(_.outputResult)` or something like that.
