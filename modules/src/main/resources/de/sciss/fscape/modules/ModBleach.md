# Bleach

Employs an adaptive whitening filter, using a simple LMS algorithm. The result is that resonant parts of a
sound are removed.

A brief overview is given for example in Haykin and Widrow, 
[Least-Mean-Square Adaptive Filters](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.568.1952&rep=rep1&type=pdf).
The module can be used in inverse mode, subtracting the whitened signal and thus retaining the resonant aspects 
of a sound. Be careful with the feedback gain. Values above -50 dB can blow up the filter.

__Warning:__ The algorithm is currently not fully tuned and especially two-way behaviour differs from FScape classic.