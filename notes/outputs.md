# Basic procedure

- stream Node provides 'flat' non-txn value, e.g. `Int`, `File`, or `AudioCue`.
- control must assemble these values and know how to build txn value from
  them, e.g. `IntObj`, `Artifact.Obj` or `AudioCue.Obj`.
- Output generating UGen gets a setter through `ub.requestOutput(key, tpe)`
- UG-builder collects output handles in a set that will be taken by
  control or outer instance

This would require that we have another sort of type repository with
translators for non-txn to txn. Complexity could be reduced if that responsibility
is shifted to the stream nodes or an interface provided by the expanding graph element.
Then we also do not introduce new sub-types of `Output` in the sense of an `Output[A]`,
we keep it simple with a `S#Var[Option[Obj[S]]]`, and that new interface produces
the `Obj[S]` when invoked with the primitive value (or invoked blank indeed).

```scala
import de.sciss.lucre.stm.{Sys, Obj}

trait OutputProvider {
  def make[S <: Sys[S]](implicit tx: S#Tx): Obj[S]
}

trait UGenGraphBuilder {
  def requestOutput(key: String, tpe: Obj.Type, p: OutputProvider): Boolean
}
```

Let's play this through with a combination of `Int` and `AudioCue`.

```scala
import de.sciss.fscape._
import lucre.graph._

val g = Graph {
  val in  = AudioFileIn(key = "in")
  val max = RunningMax(in.abs).last
  val inv = -in // whatever
  MkAudioCue(key = "audio", in = inv)
  MkDouble  (key = "max"  , in = max)
}
```

- `AudioFileIn` will request the attribute `"in"` which should be automatically
  collected in the cache key
- `MkAudioCue` will ask, in the stream initialization, for a cache entry in 
  the _session-coupled cache_ (see below); before, in `GE` expansion it will
  call `requestOutput` with `tpe = AudioCue.Obj`.
  
However let's reflect on the fact that the essential data for `OutputProvider` must be stored
in the stream node; some like `MkAudioCue` might differ as they allocate/know the file resource
earlier, but `MkDouble` clearly ties this knowledge to calling `removeNode` and `stopped`. These
calls come possibly from nodes running on different threads, and we should not communicate to them
directly. Then perhaps we provide a lucre extension for `Control` with added method
`removeNode(p: OutputProvider)`, and that provider can safely be instantiated by the calling node.
Then the control, when `nodes.length` becomes zero, can assume that all possible providers have
been collected. To avoid having to mess around with the system type, we could just iterate over
`fsc.outputs` at this last stage, or the control stores handles to the outputs requested through
`requestOutput` (yes, do the latter).

For further simplicity, we can just expose the variable nature, so the control doesn't need to
know about any impl-classes for `Output`:

```scala
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, Sys}

trait Output[S <: Sys[S]] {
  def fscape    : FScape[S]
  def key       : String
  def valueType : Obj.Type
  def value     : stm.Var[S#Tx, Option[Obj[S]]]
}
```

This works for primitive types, but does not yet deal properly with resource caching.

# Caching

We can distinguish between structural and nominal identity when talking about the cache key.
Structural equality would be based only on flat values and would have to include the `Graph`
of the `FScape` object. The advantage would be that duplicating sonification objects, for
example, does not cause cache files to be re-calculated and stored multiple times. Nominal
equality would use a tuple such as `(fscape.id, key, attr-values: _*)` instead. The advantage
would be that the cache key is relatively small. The disadvantages are that duplicating the
object implies that all caches have to be calculated again (possibly multiple times and that
cache invalidation is more difficult, because we need to actively look for updates of the
graph variable). Nevertheless, we need also a form of nominal equality for the structural
matching, because we need to provide an `accept` function that causes active eviction of the
previous cache for the same fscape object, unless we want to leave that to automatic eviction
in the provided LRU manner.

From the preceding discussion it becomes clear that using structural equality is the preferable
option. The cache key would thus be `(ugen-graph, attr-inputs, ugen-key)`. The control would take 
responsibility for calculating the UGen graph part of the key.

If we view the fscape graph as a unity, then perhaps it is much easier if we maintain _one_
key for all sub-values, i.e. `(ugen-graph, attr-inputs)`, and the `ugen-key` is merely used
for the `Output` look-up. Then it appears that the fscape object should internally store
the current key instead of calculating it ad-hoc. That way, when for example the graph is
replaced, we can easily evicted the previous cache entry, and we get a fresh (differing)
key.

## Invalidation

An `FScape` object must always observe its `graph` variable. If it is notified about
a change, it flips an internal flag that indicates that we need to revalidate the cache. The
re-validation then happens at a later point, either as a `value` in an output is accessed
(preventing an object to be emitted from `value()`), or through an active call. If re-validation yields
the old key, the flag can be flipped back.

# Re-thought

Caching needs to be fundamentally solved/implemented in SoundProcesses first. It should be
a general mechanism into which FScape plugs, because it is just one scenario. Clearly, `Workspace`
must be enhanced with a workspace-internal cache directory mechanism. And clearly, `AuralProc`
should support cached attributes in a principle manner, not by overloading it from some sub-project.

It is also clear then that a use-site must be able to trigger cache regeneration in a black-box
manner when it encounters a value, it cannot have knowledge of FScape and the way an FScape patch
is run. This should go into the `buildAsync` part of the aural proc.