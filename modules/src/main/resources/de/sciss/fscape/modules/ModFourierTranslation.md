# Fourier Translation

This module allows a translation from one time domain to frequency domain or vice versa. 
Once you transformed a sound to the Fourier domain you can apply all algorithms that you would normally
apply to the time signal. Finally you can go back to the time domain.

## Background

Jean Joseph Baptiste Fourier became famous for his theorems known as the Fourier Analysis. In his model each 
signal (e.g. a sound) can be represented by a weighted sum of sines and cosines. The Fourier Analysis
(more precisely the discrete fourier transform) calculates those sine/cosine coefficients which
we call the fourier spectrum. The forward transform is paralleled by the possibility of a
lossless inverse transform (the synthesis).

## Parameters

_Input file:_ Time domain or frequency domain signal. Generally the Fourier
transform is defined for complex signals, i.e. those represented by complex numbers. Complex numbers are made
of a so-called real and a so-called imaginary part. Because ordinary sound file formats do not support complex
numbers, I decided to use separate files for real and imag parts. Usually you start from a real time signal
(deselect "imag" in the input) and do a forward transform resulting in the complex spectrum (check "imag" in
the output). Then you manipulate the spectrum (e.g. crop a portion) and translate it backward again (here
you supply both real and imag so you should check "imag" for the input) to get a time signal (often real so
deselect "imag" for output).

_Output:_ Time domain or frequency domain depending on the direction chosen.
Note that this module always assumes complex signals independent of the checkboxes. Therefore a "complete"
spectrum is calculated: It starts at 0 Hz (DC) and goes up to half the sampling rate (the Nyquist frequency,
for 44.1 kHz sounds this is equal to 22050 Hz) followed by descending negative frequencies until we finally
reach 0 Hz again.

_Direction:_ Forward for time input/ fourier output; backward for fourier input/ time output.
