# Branching

We want to be able to create branches so that only one of them is actually executed. This is mostly needed for
dynamic adjustment based on initial conditions such as passing in a predicate from the outside (SoundProcesses).

Now, because there is no global stream of data, sampling rate, block size, etc., it's impossible (or at least
extremely hard) to 'pause' and 'resume' a sub-graph correctly. On top of that, Akka does not allow the runtime
modification of the graph.

So let us create a mechanism that supports the following:

- a conditional element that can determine from the first value of its predicate input stream, whether
  a sub-branch is executed or not

```
  val in0 = AudioFileIn(...)
  val in = If ("filter".attr(false)) {
    HPF(in0, 1000.0/sr)
  } Else {
    in0
  }
  AudioFileOut(in * gain)
```

Perhaps the easiest way to achieve this, is to consider the entire graph

```
                     _______________
                    [AudioFileIn____]
                     |_______________
                    [Broadcast______]
                     |           |
    "filter".attr    |           HPF
     |_______________|___________|__
    [If_____________________________] 
     |
    AudioFileOut

```

What we must avoid is the currently "normal" behaviour of all elements, including AudioFileIn, to start tasking
right away, even at the absence of pulls from sinks, they may start polling data of their inlets. So the `If`
would be constructed to first wait for the first package for the predicate, then determine whether it starts
polling the `if` or the `else` branch. When it does that, it will close the other inlet, allowing thus the dead
branch to shut down as well. So it's vital that the elements still install their handlers immediately, but that
they refrain from polling inputs. It would be great if `If` could send the active branch an initialisation message
indeed, so we don't need to move the initialisation check into the output handlers.

Can we run into a deadlock? Such as `"filter".attr` being used in one of the branches? I think the injected
`broadcast` would always include one block of buffering.

--------

There are side-effecting elements, say `Poll`. We need to capture those. All elements that need explicit expansion
are using `Lazy.Expander` and thus `Graph.builder.addLazy(this)`. Expansion is two steps: `GE` to `UGenInLike` 
to `StreamOut`. The latter produces `Node` which upon `createLogic` of its parent graph stage calls
`control.addNode`, which would be the place to collect the nodes into branch specific collections, so that we
can then call a kind of "launch"; probably using `getAsyncCallback`.