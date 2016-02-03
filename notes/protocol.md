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