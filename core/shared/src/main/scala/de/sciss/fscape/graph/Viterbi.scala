/*
 *  Viterbi.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package graph

import de.sciss.fscape.UGenSource.unwrap
import de.sciss.fscape.stream.{StreamIn, StreamOut}

import scala.collection.immutable.{IndexedSeq => Vec}

/** A UGen performing a generalized Viterbi algorithm. The Viterbi algorithm
  * tries to find the best path among sequences of states, by evaluating transition
  * probabilities. It runs over a predefined number of frames, accumulating
  * data of different states. It maximizes the likelihood of the terminal state,
  * and then backtracks to reconstruct the likely path (sequence of states).
  * The output is the sequence of state indices (from zero inclusive to
  * `numStates` exclusive).
  *
  * '''Note:''' This UGen must run until `numFrames` or the inputs are exhausted,
  * before it can begin outputting values.
  *
  * This implementation is generalized in the sense that instead of the canonical
  * matrices "sequences of observations", "initial probabilities", "transition matrix",
  * and "emission matrix", it takes two large matrices `mul` and `add` that contain
  * the equivalent information. These two matrices allow the UGen to operate in two
  * different modes:
  *
  * - multiplicative (as in the Wikipedia article) by damping the probabilities over
  *   time
  * - accumulative (as used in Praat for pitch tracking) by adding up the weights
  *   (if you have "costs", feed in their negative values).
  *
  * Basically the internal delta matrix is created by the update function
  * `delta = (delta_prev * mul) + add` (with the corresponding matrix indices).
  *
  * The initial delta state is zero. Therefore, in order to provide the initial state,
  * you obtain an initial vector `v` by providing an `add` matrix of `numStates x numStates`
  * cells, which is zero except for the first column filled by `v` (alternatively, each row
  * filled with the next value of `v`, or a diagonal matrix of `v`; if `v` can take negative
  * value, make sure to fill the initial `numStates x numStates` completely by
  * duplicating `v`).
  *
  * For the classical data, set `add` just to the initial matrix as explained above
  * (multiplying emitted first observations with initial probabilities),
  * and then use exclusively `mul` by passing in emitted observations multiplied by their
  * transition probabilities:
  *
  * {{{
  *     mul[t][i][j] = transitionProb[i][j] * emissionProb[observation[t]][i]
  * }}}
  *
  * See https://en.wikipedia.org/wiki/Viterbi_algorithm
  *
  * @param mul        the generalized multiplicative matrix (combining transition probabilities,
  *                   emission probabilities and observations). If only accumulation is used,
  *                   set this to 1.0.
  * @param add        the generalized accumulative matrix (combining transition probabilities,
  *                   emission probabilities and observations). If only multiplication is used,
  *                   set this to provide the initial state (see above), followed either by zeroes
  *                   or by terminating the signal.
  * @param numStates  the number of different states, as reflected by the inner dimensions of matrices
  *                   `mul` and `add`.
  * @param numFrames  the number of observations. If `-1`, the UGen runs until the input
  *                   is exhausted. This happens when ''both'' `mul` and `add` end.
  *
  * see [[StrongestLocalMaxima]]
  * see [[PitchesToViterbi]]
  */
final case class Viterbi(mul: GE = 1.0, add: GE, numStates: GE, numFrames: GE = -1) extends UGenSource.SingleOut {
  protected def makeUGens(implicit b: UGenGraph.Builder): UGenInLike =
    unwrap(this, Vector(mul.expand, add.expand, numStates.expand, numFrames.expand))

  protected def makeUGen(args: Vec[UGenIn])(implicit b: UGenGraph.Builder): UGenInLike =
    UGen.SingleOut(this, args)

  private[fscape] def makeStream(args: Vec[StreamIn])(implicit b: stream.Builder): StreamOut = {
    val Vec(mul, add, numStates, numFrames) = args
    stream.Viterbi(mul = mul.toDouble, add = add.toDouble, numStates = numStates.toInt, numFrames = numFrames.toInt)
  }
}
