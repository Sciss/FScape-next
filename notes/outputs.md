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
import graph._
import lucre.graph._

val g = Graph {
  val in  = AudioFileIn(key = "in")
  val max = RunningMax(in.abs).last
  val inv = -in // whatever
  MkAudioCue(key = "audio", in = inv)
  MkInt     (key = "max"  , in = max)
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

It is clear that we cannot prevent false retention of objects pointing to invalidated
cached values. Even if we change from `Obj[S]` to a flat value in `Gen`, we could still
lift a `File` to an `Artifact.Obj` etc. Without a real reference counting mechanism, we
must therefore rely on the use site being very careful about the use of a `Gen` value.
We must introduce an in-memory transactional use counter similar to what filecache-txn
does.

# Re-thought

Caching needs to be fundamentally solved/implemented in SoundProcesses first. It should be
a general mechanism into which FScape plugs, because it is just one scenario. Clearly, `Workspace`
must be enhanced with a workspace-internal cache directory mechanism. And clearly, `AuralProc`
should support cached attributes in a principle manner, not by overloading it from some sub-project.

It is also clear then that a use-site must be able to trigger cache regeneration in a black-box
manner when it encounters a value, it cannot have knowledge of FScape and the way an FScape patch
is run. This should go into the `buildAsync` part of the aural proc.

# Resources

So in `MkAudioCue`, how do we allocate the resource, and how to we evict it?

- say we have a `TxnProducer` in file-cache, with directory inside the workspace directory
  (in in `/tmp` for in-memory workspace)
  
The main problems enter through object duplication: Say the cache key is calculated from the structure
of the UGen graph (including structural representation of its flattened attribute inputs). Now
the `FScape` object is duplicated, for example because an encompassing `Sonification` is duplicated.
The structural hash key will be identical, the `S#ID` fields of the `FScape` objects not. If we
only use the structural key, both sonifications share the same cache, which is useful. Now the
copy is edited and the UGen graph updated. The self-observing `FScape` object might wish to evict
the cache entry at the "old" key, but it does not know that there is another copy that actually
uses the old cache entry. Should we be pessimistic or optimistic? It is clear that the user could
also delete the `Sonification` instance, and because we do not have any reference counting, there is
no way to register this deletion in the cache manager.

From the above, it should become clear that the only reasonable setup is a cache with a limit,
and an option for the user to explicitly wipe the cache. However, then there isn't much of an advantage
having a separate cache in the workspace directory as opposed to a global cache?

## Structural Equality

We do not implement `equals` on UGen at the moment. As the discussion with changing from set to
vector in the UGen graph builder has shown, there is no reasonable _universal_ equality for UGens,
but it depends on the context. We thus propose a function `UGen => Seq[Any]` that produces the
elements for structural equality in caching the graph.

This sequence will most likely be the concatenation of the recursively mapped `inputs` of the UGen
and the `rest` argument, which could map particular types in a particular way, for example a `File`
to its path, length, and modification date.

### Analysis: `rest` usage

In "core":

- `Int` (`numChannels`, `opID`)
- `File` (input, i.e. existing)
- `AudioFileSpec`
- `File` (output, i.e. possibly not existing)
- `String` (label)

In "lucre":

- `ActionRef` (__not structural__)
- `OutputRef` (__not structural__)

N.B.: `Attribute` is a nested graph element that produces a `Constant` in the UGen, so this is
already structurally solved. Similar: `AudioFileIn.NumFrames`, `AudioFileIn.SampleRate`.
`AudioFileIn` obtains a flat `File` instance and falls back to the core equivalent graph element.
Dito for `AudioFileOut`.

Clearly, `ActionRef` and `OutputRef` are not structural values. Since both denote _output_ type values,
strictly speaking they need not be part of a structural _input_ specification. That is, they do not influence
what the graph "calculates", although of course they influence what happens with the calculated output.

These two types are unfortunate in that we could imagine they would appear not in the UGen expansion, but
in the stream instantiation. However, we want to be able to trace missing inputs at the earlier point.

An `Action` is the perfect example for an inherently opaque object, there is no (reasonable) way we can produce a
flat structural representation from it. In the case of `...Ref`, we should simply add the string keys to
the `rest` structure.

If we don't want to rely on the `Product` serializer known from synth graphs, we should introduce a small
sum type `Structure` that wraps the above types, giving explicit `ImmutableSerializer` instances.

```scala
import de.sciss.serial.ImmutableSerializer

object Structure {
  case class Int(peer: scala.Int) extends Structure {
    type A = scala.Int
    def serializer = ImmutableSerializer.Int
  }
}
trait Structure {
  type A
  def serializer: ImmutableSerializer[A]
}
```

etc. And then the `rest` argument becomes `structure: List[Structure]`.
The disadvantage of course is that we need an extensions mechanism if we want to keep `Structure` open
(e.g. for adding custom types in SysSon). Perhaps using a `Product` (tuples...) is better then? Binary
compatibility is not a big issue as we can simply declare a cache invalid if we can't deserialize it.

## How does `MkAudioCue` obtain its output file?

- the `stream.Builder` (sub-class) should have the create-temp-file method for a file-cache
- that temp file must be registered, so it is deleted if the stream fails
- the `Output.Provider` must be changed to additionally provide part of the cache value
  (the temp file in this case)

The confusing bit stems from the file-cache API that must be interwoven with the `run` of `FScape` (or
indirectly through `GenView#start()`). In particular, we need defined behaviour if the cache key is still
valid when running the graph. The cleanest would be to change `requestOutput` to indicate a difference
between missing output and valid output. If the output is already valid, we drop the UGen, or insert a
dummy UGen if a UGen output is required (e.g. `Frames` for `MkAudioCue`.)

There is chicken-and-egg problem, though, because the UGen structural key is obtained exactly by
expanding the element graph, i.e. an action that invokes `requestOutput`. In summary, we may _not_ allocate
resources before the stream stage, pure UGen graph expansion should be side-effect free.

__Proposal:__ Explicitly running the `FScape` when the key is already valid should be transparent to the
graph; all UGens runs as normal, however the clean-up stage then deletes the files created in the cache
directory, instead of submitting them to the cache value. For simplicity we could add a `createCacheFile`
method to `stream.Builder` in "core", so we do not need to sub-class this.

## How do we prevent evicting used files?

- remove `value` method from `Gen`
- i.e. enforce going through `GenView`
- perhaps `Obj[S#I]` is the way to go (look into `AuralProc` to see if this is easily fitted in?)
- in the view we must call `acquire` and `release` to obtain _any_ value (for the sake of consistency across
  all types of cached objects)

# New `acquire`/`release` API

- client calls `acquire` on an `OutputView`
- output-view calls `acquire` on the `GenContext` for the `FScape` instance, yielding an `FScapeView` (internal API)
- fscape-view when created, evaluates and observes the graph and the connected attributes. (yes?)
- fscape-view thus maintains a cache key (or the current plus still held old keys)

# UGB.Input

The problem now is there is no "top-down" instance that can enhance the attribute resolution as was the case
with `AuralSonificationImpl` that wraps `AuralProcImpl`. There must thus be another way to inject `UGB.Input`
extensions.

The current command chain would be as follows:

- `AuralProcImpl.requestInput`
- we must extend `UGB.Input.Scalar.Value` and possibly others to include `async` as a constructor parameter
- it would find a `Gen`
- it would somehow have access to a `GenContext` (__how?__)
- it would call `GenView(gen)` and obtain a `GenView`.
- it would then call `value` to see if a cached value is available
    - if yes, check if it was successful
        - if yes, return a synchronous `UGB.Value`
        - if no , fail the aural proc build
    - if no, call `reactNow` and return an asynchronous `UGB.Value`
        - in `buildAsyncAttrInput`, wrap the `GenView` in an `AsyncResource` and attach it

Furthermore, we need to find the injection point for `UGB.Context`. Since `FScapeView()` is called from
`OutputGenViewFactory`, the logical point is to extend `FScape.genViewFactory` to take such a context?
Can that context be generated globally and independently from parent object knowledge. In other words, 
how do we get from an `FScape.Output` to a `Sonification` in order to access `sonif.sources` and finally
find a `Matrix`?

The only possible entry point here would be through `AuralSonification` and the `findSource` method of its
implementation. Of course, a dirty hack is always to set a `TxnLocal`... This implies that the matrix is
looked up in the same transaction as the aural view's `play` call. Is this so? The following snippet from
`OutputGenViewFactory` suggest that this is __not__ the case:

```
val fscView = context.acquire[FScapeView[S]](_fscape) {
  FScapeView(_fscape, config)
}
```

In other words, the current assumption is that all inputs are available through `fscape.attr`.
__But:__ There is nothing wrong with installing, in SysSon, our own `genViewFactory` that changes the
behaviour of `apply`, looking for the txn-local `AuralSonification`, and then creating a sub-class of
`FScapeView` that deals with the specific matrix look-ups; and here we'd also have the particular 
`UGB.Context` built-in.

This is all a bit messy and complex, but the easiest solution I can think of in the current SP design.
This means, we should make `FScapeView` kind of the equivalent of `AuralProcImpl` in that it has default
implementation for `requestInput` and exposes some methods that can be overridden.
