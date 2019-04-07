# Tape Speed

You can either resample a sound to be of shorter duration/higher pitch or longer duration/lower 
pitch. This is similar to running an audio tape at higher or lower speed.

This module uses band-limited resampling using sinc-interpolated low-pass filters, as 
described by Julius O. Smith.

## Parameters

_Input/output files:_ Input sound file and speed adjusted output file.

_Speed:_ Relative speed / in percent. 100% would be original speed and pitch,
200% would be double speed, double pitch, and half duration. 50% would be half speed,
half pitch, and double duration.

_Quality:_ The algorithm performs a band-limited sinc interpolation which is the most accurate 
method for resampling. A sinc is of infinite length, therefore we have to truncate it. Shorter 
FIRs speed up the processing but result in slightly worse quality and a broader low-pass 
transition band. 
