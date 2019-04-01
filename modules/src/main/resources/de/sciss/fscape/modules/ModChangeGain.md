# Change Gain

This module simply changes the gain (volume) of a sound file. Besides it can be used
to find the peak sample and the rms energy of a file.

Parameters:

- __Input__: Sound file to be volume adjusted.
- __Output__: Destination sound file and gain control.
  If the gain type is set to "Immediate" then the input is directly adjusted according 
  to the relative dB amount.
  If the mode is "Normalized", the input is first normalized and then adjusted by the 
  dB amount which acts as a "ceiling" or "headroom".
