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
    
- `startNextWindow` returns `step`
- `copyInputToWindow` - consume `winSize` samples; as in `Sliding` we maintain a list of "open" windows,
  and here we append only to the active one, i.e. the first with `inRemain > 0`.
- `processWindow` - returns `windows.last.availableOut1`
  ; this becomes `readFromWinRemain` and determines the number of frames
  that must be transported to the outlet before a going back to `copyInputToWindow`,
  thus controls the back pressure.
- `copyWindowToOutput` - output `step` samples; summing the content of the list of "open" windows
- `startNextWindow`  (loop -- remain at `step`)

What is `availableOut1`? Observation: `offOut` must be zero at the time `processWindow` is called,
and `offIn` might be the most recent `min(size, step)` (or zero if the previous windows are still
being filled!). I.e. if the last window is full (`inRemain == 0`) it _should_ return `step`. We don't 
need to store that `step` in the `Window` structure, but can just poll the most recent global `step` value.
Otherwise it should be `offIn`.

Example of the regular 1/2 overlap above:
- start-window -> `step`
- create new window and fill with `step` frames.
- `processWindow` returns `step`; copy those to output
- create new window; append `step` to first window
- `processWindow` returns zero (`availableOut` for second window is zero)
- append `step` to second window
- `processWindow` returns `step`; add last `step` from first window and first `step` from second window to output
- remove first window

How to support `step > winSize`

    |xxxxx
            |xxxxx
                    |xxxxx
                            |xxxxx  |

It looks actually like this should already be supported with the above algorithm, except that
we need to take care to add zero padding in `copyWindowToOutput`.

Example of the regular 1/2 overlap above:
- start-window -> `step`
- create new window and fill with `min(size, step)` frames.
- `processWindow` returns `min(size, step)`; this must be corrected to actually return `step`!
- copy and remove window
- create new window; and repeat

Irregular windows:

    |xxxxxxx|xxx|xxxxxxx|xxx|
    
    |xxxxxxx
       |xxx
          |xxxxxxx
             |xxx|

## Normalize

    proc -> abs              -> RunningMax -> LastValue -> reciprocal -> *headRoom  \ * -> DiskOut
         -> PersistentBuffer                                                        /

    val proc = ??? : GE
    val max  = RunningMax(proc).last
    val gain = headRoom / max    // IfGt(max, 0, max, 0) ?
    val buf  = PersistentBuffer(proc)  // FullBuffer ? BufferAll ?
    val sig  = buf * gain
    DiskOut(sig)

## Fourier

- Create four temporary files 0 to 3
- Write first half of input to 0, second half to 1
- Pad these files to fftSize/2
- Define `memAmount` as a power of 2; `fftBuf = new float[3][memAmount]`
- Run routine `storageFFT`
- Delete temporary files 0 and 1
- Output file 2 (first half) followed by 3 (second half)

StorageFFT:

- calculate `mMax`
- first loop where `n2` goes from `len` down to `memAmount`; transforming two files into two files

## OffsetOverlapAdd

A variant of overlap-add that allows negative step sizes, by restricting step size to a min/max boundary.
Perhaps we only need one extra parameter ("min-step").
    
    OffsetOverlapAdd(in, size, step, offset, minOffset)

Where `minOffset` is only accepted at initial time. As in `OverlapAdd`, `step` may be updated
repeatedly but is restricted to positive numbers.

(What we should also do in `Sliding`?) We may have `size + offset-range >> step`, thus to avoid
excessive memory usage, we'll use a dynamically growing, single circular buffer. At any time, 
the dynamic size be `>= size + offset - minOffset`. It will be easier we track overall `framesRead` and 
`framesWritten`. If we want to make sure that `framesRead|Written % bufSize == bufIndex` at all times,
we have to ensure that content is newly laid out upon buffer growth.

We can then technically split `writeToBufIndex = framesRead + (offset - minOffset) + windowWritten`, 
where `framesRead` is updated after the window finishes, so that `frameRead - framesWritten` gives the 
amount of data available for pushing to out. As a consequence, output will be aligned by `minOffset`
which makes sense but must be stated in the docs. If the caller wants to "keep the length" of some
original input, one would thus `.drop(-minOffset)`. E.g. if `minOffset = -winSize/2` then one would
have to `.drop(winSize/2)` which is easy to understand.

The back-pressure for pulling could be defined in terms of some `def canStartNextWindow = outRemain == 0`.
So we have the input-copying loop, and when `writeToWinRemain` becomes zero, `outRemain` becomes `step`.
Or if `inRemain` becomes zero and `isClosed(in0)`, then we start flushing by setting `outRemain` instead
to cover to the end of the written data. Technically, we'll never need to use `frameRead - framesWritten`
as defined above, because through the pressure cycle, we'll always set `outRemain` to `step`.

This algorithm then naturally expands to `OverlapAdd` where `offset = minOffset = 0` at all times.