# Signal Generator

A utility module to produce a number of standard sound signals.

## Parameters

_Output:_ The output sound file to generate.

_Duration:_ The length of the generated signal in seconds.

_Amplitude:_ The nominal amplitude of the signal. Normally you would use 1.0 for
an optimal use of resolution. Values greater than 1.0 will clip the output unless
"float" is chosen as sample format.

_Signal Type:_ Currently the generator provides a sine oscillator, an aliased
(not band-limited) sawtooth oscillator, a single sample click generator ("metro"),
a constant signal at the specified amplitude ("dc") or white noise.

_Frequency:_ For the period signals (sine, sawtooth, click train), their frequency in Hertz.

_Phase_: For the period signals (sine, sawtooth), their initial phase between zero and one.
For example, to produce a cosine signal, choose the sine oscillator with a phase of 0.25.

_Fade in/out:_ If greater than zero, the generator will linearly fade the signal in and out
at the begin and end of the generated file.
