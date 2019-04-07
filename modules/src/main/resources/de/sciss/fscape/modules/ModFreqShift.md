# Frequency Shift

Based on a Hilbert transform, this module takes an input signal and internally calculates two output 
signals that have a constant 90 degrees phase difference across the whole spectrum. 
It then modulates by a complex exponential, producing a single sideband 
modulation (SSB) also known as frequency shifting. The input signal's spectrum is shifted
up or down linearly.

## Parameters

_Input file:_ The file to transform

_Output file:_ The frequency shifted signal.

_Shift:_ The frequency by which to shift the spectrum up (positive numbers) or
down (negative numbers).
Note that frequency shifting is _not_ pitch transposition. That means that harmonic 
relationships are destroyed resulting in bell-like spectra with non-linear partial spacing, 
the kind of sound you know from ring modulation.

_Anti-aliasing:_ 
__Not yet implemented!__
When frequency shifting is applied we will normally want to
have a protection against aliasing. Aliasing occurs for example when you shift a signal downwards 
by 100 Hz and the signal has significant energy below 100 Hz. Think of a partial at 30 Hz. When you 
downshift it by 100 Hz the mathematical outcome is -70 Hz and physically you'll hear it at +70 Hz. 
By checking this gadget all signal content that would generate aliasing is eliminated. There are 
applications where you want to have aliasing. For example try to shift a signal up or down half the 
sampling rate (22050 Hz for a 44.1 kHz sound) - when you allow aliasing the result will be an 
inverted spectrum! (1000 Hz becomes 21050 Hz, 2000 Hz becomes 20050 Hz etc.).

Note that because of the anti-aliasing filter the bass frequencies are slightly attenuated. Try to 
feed this module with white noise and see what comes out. If you don't like it apply a bass 
compensation filter afterward.
