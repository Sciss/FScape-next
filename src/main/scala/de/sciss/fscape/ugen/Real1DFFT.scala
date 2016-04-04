package de.sciss.fscape
package ugen

import scala.collection.immutable.{IndexedSeq => Vec}

trait Lazy extends Product {
  private[fscape] def force(b: FScapeGraph.Expander): Unit
}

object FScapeGraph {
  trait Builder {
    def addLazy(x: Lazy): Unit
  }

  trait Expander {
    def addProcess(p: Process): Unit
    def visit[U](ref: AnyRef, init: => U): U
  }

  /** This is analogous to `SynthGraph.Builder` in ScalaCollider. */
  def builder : Builder  = ???
  /** This is analogous to `UGenGraph.Builder` in ScalaCollider. */
  def expander: Expander = ???
}

trait Expander[+U] extends Lazy {
  // this acts now as a fast unique reference
  @transient final private[this] lazy val ref = new AnyRef

  // ---- constructor ----
  FScapeGraph.builder.addLazy(this)

  /** A final implementation of this method which calls `visit` on the builder,
    * checking if this element has already been visited, and if not, will invoke
    * the `expand` method. Therefore it is guaranteed, that the expansion to
    * ugens is performed no more than once in the graph expansion.
    */
  final private[fscape] def force(b: FScapeGraph.Expander): Unit = visit(b)

  /** A final implementation of this method which looks up the current ugen graph
    * builder and then performs the expansion just as `force`, returning the
    * expanded object
    *
    * @return  the expanded object (e.g. `Unit` for a ugen with no outputs,
    *          or a single ugen, or a group of ugens)
    */
  final private[fscape] def expand: U = visit(FScapeGraph.expander)

  private def visit(b: FScapeGraph.Expander): U = b.visit(ref, makeSignal)

  /** Abstract method which must be implemented by creating the actual `UGen`s
    * during expansion. This method is at most called once during graph
    * expansion
    *
    * @return  the expanded object (depending on the type parameter `U`)
    */
  protected def makeSignal: U
}

object UGen {
  trait ZeroOut   extends UGen[Unit]
  trait SingleOut extends UGen[Signal] with GE
}
/** This is analogous to `UGenSource` in ScalaCollider. */
trait UGen[+U] extends Expander[U] {
}

trait Process

/** This is similar to `UGenIn` in ScalaCollider. */
trait Signal extends GE {
  final private[fscape] def expand: Signal = this
}

object GE {
  trait Lazy extends Expander[Signal] with GE
}
sealed trait GE extends Product {
  private[fscape] def expand: Signal
}

case class ConstantInt   (i: Int)    extends Signal
case class ConstantLong  (n: Long)   extends Signal
case class ConstantDouble(d: Double) extends Signal

case class Real1DFFT(in: GE, size: GE) extends UGen.SingleOut {
  protected def makeSignal: Signal = {
    val p: Process = ???
    FScapeGraph.expander.addProcess(p)
    ???
  }
}
