# Case study - Kriechstrom

Let's begin with a medium complex process. 

- it is driven by frames-written
- pass 1 - region detection
- pass 2 - synthesis

Technically it should be split into two modules for simplicity, a module
`KriechAnalysis  extends Module[Seq[Region]]` and 
`KriechSynthesis extends Module[Signal]`

# Case study - Step Back

The same: two passes

# Case study - Laguerre

If we modulate `warp`, that affects `transLen`, `inputStep`, `outputStep`, `outputLen`, `fltLen`, `b0init`.
However, since `warp` is clipped to -0.98 ... +0.98, we can re-allocate all buffers.

# Real1FFT

    case class Real1FFT(in: GE, size: GE, padding: GE = 0)
    
## Logic

    inIn: Inlet[BufD], inSize: Inlet[BufI], inPadding: Inlet[BufI]
    bufIn: BufD, bufSize: BufI, bufPadding: BufI
    
- `onPush`: `pending--`
- when `pending` becomes zero, we can check if we are ready to grab all
- that check is that `inOff == bufIn.size` (previous buffer content was all transformed)
- if `false` do nothing
- if `true`, call `grab` on all inlets (except the aux ones if they have been closed)
- check if we can write to the fft-buf
- that check is `fftOutOff == fftSize`
- if `false` do nothing
- if `true` 

## Other Resources or Projects

- http://essentia.upf.edu/documentation/streaming_architecture.html (somewhat between FScape and pure Akka Stream,
  e.g. ports are typed, the network must be fully connected, however one can connect a single source to several sinks)
  ; paper reference: http://hillside.net/plop/2006/Papers/Library/audioPatterns_20060809.pdf
  ; mentions other approaches: CLAM, OSW
- 'semantic ports' versus non-semantic ports (different types of flow dynamics for different data types,
  e.g. audio versus message in Pd; stream versus event)
- it's a good paper examining most of the questions (e.g. copying versus managed references, timing and multi-rate)
- they are not as flexible as FScape as for example the rate-factors per port seem to be fixed, there is no
  notion (?) of varying or irregular production rate 
- http://clam-project.org/wiki/Frequenly_Asked_Questions
- Marsyas - the API looks pretty horrible and completely untyped ("stringly-typed"):
  http://marsyas.info/doc/manual/marsyas-cookbook/Playing-a-sound-file.html#Playing-a-sound-file
  (seems to rely instead on a text script language)
- http://marsyas.info/about/publications.html ; http://speech.di.uoa.gr/ICMC-SMC-2014/images/VOL_1/0325.pdf
- Faust
- http://web.eecs.umich.edu/~gessl/georg_papers/ICMC12-Time.pdf
- (SndObj: http://sndobj.sourceforge.net)
