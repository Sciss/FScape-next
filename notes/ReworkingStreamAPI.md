# Reworking Stream API (06-Jul-2019)

## Reworking the `GraphStageLogic`

Situation: We have simplified the assumption of blocks of input data as coming in aligned packages.
For example, take `FilterIn2Impl`, used in many places, like `LPF`. `readIns()` cannot take into
account if the `in` and the `freq` arguments run out of phase, for whatever reason. This can
cause headaches and weirdness for the user who needs to know these technical simplifications.

Also situation: We have worked again from complete scratch in recent UGens to avoid this problem,
for example in `BinaryOp`. While we tried to create useful new abstractions for the input handlers,
essentially we ended in callback hell.

It appears to me that the root problem is a too tight coupling to Akka's API. The fact that after the
introduction of `If` and late calling of `pull`, we have a much more indeterminate situation, all the
checking for `isAvailable` when an inlet closes is really horrible.

An idea could be to rework the API to take away all the Akka'ish notion of pulling and grabbing
inlets, so that we can focus the stream code again on the DSP and remove all the boilerplate which
creates lots of bugs. If we only had to deal with `BufLike`, and the there were abstractions that
better handle the alignment of buffer sizes, this would simplify a lot of things.

## Introducing `reset`

Since such reworking means rewriting a lot of the streaming code (its "infra-structure"), it could
be a good moment to introduce the idea of resettable stream as well, as established in 'Patterns'.
It may be challenging, because things we already built, like turning off "dead" branches in `If`,
lo longer applies (they may becomes "alive" again when reset).
