# change gain

It would be good if we can unify all the below in one static graph (blueprint).
That is, we need branching logic depending on gain mode.

## immediate mode

    val disk = DiskIn(...)
    val mul  = disk * gain
    DiskOut(mul)
    
## normalized mode

### unknown max

    val disk1 = DiskIn(...)
    val max   = reduce(disk1, _ max _)
    val disk2 = DiskIn(...)
    val gain1 = gain / max
    val mul   = disk2 * gain1
    DiskOut(mul)
    
It would make sense if DiskIn could already be
created with a variable number of outlets such
that each outlet can read at its own pace? this
would save resources.

### known max

- similar to immediate-mode

# make loop

The naive way of splitting the input and dropping
the right path until we reach the final section won't
work because the system will block:

    val disk    = DiskIn(...)
    val split   = Broadcast(disk, 2)
    val left    = split.out(0)
    val right   = split.out(1)
    val end     = right.drop(length - fadeLen)
    val fadeIn  = left * envIn
    val fadeOut = end * envOut
    val sig     = fadeIn + fadeOut
    DiskOut(sig)
    
- We need to introduce an abstraction for random access.
- DiskIn should have a materialised value of type `AudioFileSpec`.

# amplitude shaper

Probably having a working implementation of this module addresses most problems
(minus random access), like branching and conditionals:

    val in  = DiskIn(...)
    
    def mkEnv(x: UGenIn) = smooth(x, smoothing)
    
    val env = Switch(SourceMode, {
      // input
      mkEnv(in)
    }, {
      // second sound file
      val in2 = DiskIn(...)
      mkEnv(in2)
    }, {
      // envelope file
      val in2 = DiskIn(...)
      in2
    }, {
      // envelope
      EnvGen(...)
    })
    
    val env1 = If(Inversion, { 1 - env }, env)
    val gain = If(Replace, {
      val envIn = mkEnv(in)
      env1 / envIn
    }, {
      env1
    })
    val gain2 = gain min maxBoost
    
    val sig = in * gain2
    DiskOut(..., sig)
    
    If(EnvOutput, {
      DiskOut(..., env)
    }, ())
    
## If

    // init-time select
    case class ISelect(key: String, branch0: Graph, branch1: Graph) extends GE
    
Which may be created from syntactic sugar. The problem is `Graph` does not exhibit a particular outlet.
Therefore, the syntactic sugar should distinguish function return types like `play { }` in ScalaCollider.
Then we could have simply different objects such as `ISelectZero` and `ISelectOne`.

## Overlap-Add

The counter-part to `Sliding`.
Can we use `WindowLogicImpl`?

    |xxxxx|xxxxx|xxxxx|xxxxx|
    
    |xxxxx
       |xxxxx
          |xxxxx
             |xxxxx|
    
- `startNextWindow` returns `winSize` (NO: returns `step`)
- `copyInputToWindow` - consume `winSize` samples; as in `Sliding` we maintain a list of "open" windows,
  and here we append to all of them
- `processWindow` - a no-op? returns `step`
  ; this becomes `readFromWinRemain` and determines the number of frames
  that must be transported to the outlet before a going back to `copyInputToWindow`,
  thus controls the back pressure.
- `copyWindowToOutput` - output `step` samples; summing the content of the list of "open" windows
- `startNextWindow`  (loop -- remain at `winSize`)

How to support `step > winSize`

    |xxxxx
            |xxxxx
                    |xxxxx
                            |xxxxx  |

It looks actually like this should already be supported with the above algorithm, except that
we need to take care to add zero padding in `copyWindowToOutput`.