# Convolution

Convolution is the one of the fundamental algorithms in digital signal processing. It means to
filter a sound with a finite impulse response. Examples for filters include frequency equalization, reverb,
delays, creating an output that contains the spectral information common to both the input and
the impulse response.

This is arguably the most popular module of FScape, because convolution is very versatile, and you can easily create
a variety of interesting sounds and textures.

## Parameters

_Input:_ Sound to be filtered with an impulse response.

_Impulse response:_ Second sound file to be used as the "impulse response" for the FIR filtering. Naturally input 
file and impulse response can be exchanged while the output is the same. Use the shorter file for impulse response 
to make processing less RAM expensive. An impulse response can be a "normal" filter IR like a low pass, high pass 
etc. (something produced by the _FIR Designer_ module), the response from a delay or reverberation system or any 
other sound snippet. Convolution means the spectral characteristics of the IR (both amplitude spectrum and phase 
response) are superimposed onto the input file. When using normal sounds the output tends to emphasize the low 
frequencies unless the IR has a flat spectrum. It tends to emphasize the frequencies that exist both in the input 
and the IR and cancels those frequencies which are not present in either file. Every introductory text about DSP 
explains the details of convolution.

_Output:_ Input convolved with IR.

_File length:_ A normal non-circular convolution results in an output that is as long as the sum of the input 
length and the impulse length (minus one sample). Often you want create an output file that has the same length as 
the input, then choose 'Input'. 

_Morph:_ Means the IR file is split up into an integer number of equal length impulses, and the process goes linearly
through these "sub-impulses", cross-fading them one by one. For example, if the number of pieces is three, then in
the output file, the beginning of the input file will be convolved with the first "piece" (one third) of the impulse 
response, the middle will be convolved with the second or "middle" piece, and the end will be convolved with the
last "piece". To avoid clicks and jumps, a 'Window Step' is selected. For each step, a new interpolated (cross-faded)
impulse response is calculated from the closest two pieces. For fast movements you'll need a small window size to 
avoid clicks, for slow movements use bigger windows to increase processing speed.

_Minimum Phase:_ When checked, the impulse response (or each piece when morphing) is transformed into a minimum phase
version of itself. This is process credited to Julius O Smith III, using some manipulation in the "cepstral" domain,
minimising the group delay of the impulse response; basically, the timbral properties are preserved, while temporal
extent and smearing is minimised.
