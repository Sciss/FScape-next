# Remove DC

Removes "direct current" (DC) offsets in an input file. It uses a one pole filter,
there it adapts continuously to the near-zero frequency energy of the signal.

## Parameters

_Input file:_ The file to transform

_Output file:_ The frequency shifted signal.

_Filter Strength:_ A time constant in milliseconds for the -60 dB point of the
feedback filter. Lower values mean faster adjustment, higher values mean slower
adjustment of the filter. You can almost always leave this at the default value
of 30 ms.
