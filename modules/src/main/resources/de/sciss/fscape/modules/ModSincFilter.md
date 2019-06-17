# Sinc Filter

This module applies a windowed FIR sinc filter. These filters have very steep
slope ("brick wall").

Future versions will add a roll-off parameter and a 'minimum-phase' option.

## Parameters

_Input file_: The file to filter

_Output file_: The filtered signal.

_Type_: Filter type (low pass, high pass, band pass, or band stop)

_Cutoff Freq_ or _Lower Freq_: The cut off frequency in Hz in the case of the low pass and high
pass filter, or the lower frequency of the pass / stop band in the case of band pass and band stop filter.

_Upper Freq_: When using band pass or band stop filter, their upper frequency in Hz.

_Zero Crossings:_ Number of zero crossings on each side of the symmetric filter kernel, effectively
determining the FIR truncation length. Larger numbers produce better filter approximation at the cost
of more rendering time.

_Kaiser β_: The parameter of the Kaiser window applied to the truncated FIR. Typically between 4 and 10,
with smaller values giving steeper slope and larger values giving better stop-band attenuation.
(See [Wikipedia](https://en.wikipedia.org/wiki/Kaiser_window)).
