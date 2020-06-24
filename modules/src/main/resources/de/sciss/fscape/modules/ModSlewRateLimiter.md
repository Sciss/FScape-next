# Slew Rate Limiter

This module applies a single slew rate limiter to a sound file. The slew rate is
the amount the waveform changes per sample. For instance, if the input sound
would abruptly change from samples of value zero to samples of value one, and
the limit was 0.1, the output would produce a ramp from zero to one in steps of
0.1 (the ramp length would be 10 samples).

## Parameters

_Input:_ Sound file to be limited.

_Output:_ Destination sound file.

_Limit:_ The maximum "amplitude" of the slew rate.

_Remove DC:_ Whether to remove the DC offset that may result from the limiting or
not (enabled by default).
