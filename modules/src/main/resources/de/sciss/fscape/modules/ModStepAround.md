# Step Around

__Note: in experimental state, and very slow.__

Rearranges a file by iteratively matching a chunk at its beginning with a 
chunk further into the file, within given search ranges, looking for the local
maximum in the cross-correlation of the Mel-frequency cepstral coefficients (MFCC).

An iteration begins by copying a 'minimum fragment length' from the beginning
of the current "database" (initially the input sound) to the output,
then searches over a period given by 'maximum source search length' for a matching
source chunk, and over a period ahead by 'min jump length' and extending up to a
'maximum destination search length' for a destination chunk. The chunks are compared
by performing a cross-correlation over a sliding window of 'correlation length'
duration. This is an eager search, and thus may be very slow. Once the destination
chunk has been identified, a cross-fade from the source to the destination chunk
is made and appended to the output file. Everything that has been copied to the
output is then removed from the "database", and the procedure is repeated,
until the "database" is exhausted, yielding an output file approximately the size
of the input file (shorter due to the cross-fades).

This process thus resembles the classic FScape 'Step Back' algorithm, but instead
of a strict reverse order of chunks, it is possible to move multiple times through
the file, giving name to the module as stepping 'around'.

## Parameters

_Input:_ The file to be rearranged. For the cross-correlation calculations, a
mono-sum is formed internally.

_Output:_ The rearranged signal.

_Min. Fragment Length:_ In each iteration, this amount in seconds is copied to the
output file. In other words, no fragment in the output is shorter than this duration.

_Correlation Length:_ The duration of the sliding cross-correlation window in seconds.

_Max. Source Search Length:_ The maximum period in seconds within which, in each
iteration, the source chunk is determined. In other words, no fragment in the output
is longer than this duration plus the 'minimum fragment length'.

_Max. Destination Search Length:_ The maximum period in seconds within which, in each
iteration, the destination chunk is determined. This is clipped to the file length.
Note that the number of cross-correlations is proportional to the product of source and
destination search length, so making these values very large explodes the search space
and slows down the rendering significantly. The shorter this value, the more local the
search is, as no sound chunks are matched that are further apart than this plus the
'minimum jump length' in the input sound.

_Min. Jump Length:_ The minimum distance between source and target chunk in the search
process, in seconds. If the input sound is slowly changing, chances are that high cross-correlations
are found between immediately neighbouring chunks. If this behaviour is to be excluded,
one can set a significantly large jump or "gap" length.

_Cross-Fade Length:_ For each pasted chunk, a cross-fade is applied to avoid clicking,
and to allow for a smooth transitions. The length of the cross-fade in seconds is given
by this parameter. It will be clipped to be no longer than the 'correlation length'.

__Note:__ FFT size (2048), step-factor (1/2), Mel frequencies (42 between 100 Hz and 14 kHz) and 
coefficients (21) are currently fixed, but will be made available in a future version.
They can be edited in the FScape object directly, if necessary.
