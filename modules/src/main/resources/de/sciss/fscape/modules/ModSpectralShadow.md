# Spectral Shadow

Performs spectral shadowing or filtering by damping a "background" sound with a
"foreground" sound. Whenever the foreground is stronger than the noise floor, and
the ratio between background and foreground is above a distance threshold, the
background is attenuated.

## Parameters

_Foreground Input:_ The file used to "shadow" the background

_Background Input:_ The file that is "shadowed" by the foreground. It should have the
same duration as the foreground input.

_Output file:_ The filtered signal.

_Blur Time:_ Temporal blurring of the shadow

_Blur Freq:_ Spectral blurring of the shadow across frequencies

_Noise Floor:_ The energy threshold in decibels for the foreground to be assumed
"active"

_Fg/Bg Distance:_ The minimum ratio between foreground and background energy
in decibels for the shadowing to begin. The lower the actual ratio
(the louder the background compared to the foreground), the stronger the background 
will be attenuated.
