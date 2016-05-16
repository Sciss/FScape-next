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
