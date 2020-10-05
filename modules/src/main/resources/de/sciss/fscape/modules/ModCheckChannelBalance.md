# Check Channel Balance

This module reports the peak amplitude and RMS energy for each channel of
a sound file. It can be used for stereo or multi-channel microphone recordings
to obtain adjustment parameters for imprecise channel gain settings.

## Parameters

_Input:_ Sound file to be volume adjusted.

_RMS HPF:_ Allows to insert a high-pass-filter before the RMS measurement stage.
This may be useful if you have a lot of infra sound or low frequency rumble on the 
recording and want to exclude that from the RMS measurement. A value of zero indicates
no filtering.

## Display

_Peak_: Shows the peak amplitude in decibels (full scale) for the channels of the
input sound file. Channels are separated by commas. Peak is always measured directly from
the absolute sample values.

_RMS_: Shows the root-mean-square (energy) in decibels (full scale) for the channels of the
input sound file. Channels are separated by commas. RMS is measured after (optionally) applying
a high pass filter.
