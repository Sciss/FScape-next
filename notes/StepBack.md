    xs = x1, x2, x3, ... xN

    Assemble(in: GE, pos: GE, trig: GE)

vs

    Assemble(in: GE, pos: GE)  
    
where `pos = (start1, stop1, start2, stop2)` ...

or

    Assemble(in: GE, start: GE, stop: GE)

    val ys    = xs * frameFactor
    val sizes = ys.differentiate
    val a     = Assemble(in = ???, start = ys.init, stop = ys.tail)
    val fd    = GenWindow(sizes, ...)   // better: EnvGen
    val w     = a * fd
    val lap   = OverlapAdd(w, sizes, sizes - crossFades)
    
Like `Slice` but with random access. (`Slices`?)

    val a = Slices(in, Sliding(ys, 2, 1))
    
-------------------------------

Emits triggers when a local maximum/minimum is detected.
Fuses multiple detected points within a sliding window
of `size` into single triggers.

    DetectLocalMax(in: GE, size: GE)
    DetectLocalMin(in: GE, size: GE)
    