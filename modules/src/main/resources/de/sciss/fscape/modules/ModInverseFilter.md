# Inverse Filter

Creates a filter impulse response with frequency characteristics inverse to an
input file. The resulting response can be used in a convolution process, for example
to remove an annoying background signal (particular frequencies) in a sound. The
kernel is calculated by taking the FFT and inverting the magnitudes, then creating
a minimum phase version of the inverse FFT.
A linear amplitude fade-out is applied from the beginning to the end of the kernel.

## Parameters

_Input file:_ The file of the sound whose spectrum to invert.

_Output file:_ The resulting filter kernel.

_Max. Length:_ The maximum length of the filter kernel in milliseconds. The output
file is either this length or shorter, if the FFT of the input signal is shorter.

_Max. Band Boost:_ For each FFT band, the maximum boost applied when inverting the
magnitude. For example, if you had a synthetic input sound with particular parts of
the spectrum having zero or near-zero magnitude, this would result in an infinitely
high magnitude in the inverted spectrum, therefore we apply this clipping.
