package de.sciss.fscape.ugen

import de.sciss.fscape.{FScapeGraph, FScapeProcess, Signal}

import scala.language.implicitConversions

trait Lazy extends Product {
  private[fscape] def force(b: FScapeProcess.Builder): Unit
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
  final private[fscape] def force(b: FScapeProcess.Builder): Unit = visit(b)

  /** A final implementation of this method which looks up the current ugen graph
    * builder and then performs the expansion just as `force`, returning the
    * expanded object
    *
    * @return  the expanded object (e.g. `Unit` for a ugen with no outputs,
    *          or a single ugen, or a group of ugens)
    */
  final private[fscape] def expand: U = visit(FScapeProcess.builder)

  private def visit(b: FScapeProcess.Builder): U = b.visit(ref, makeSignal)

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

object GE {
  trait Lazy extends Expander[Signal] with GE

  implicit def fromInt(x: Int): ConstantInt = new ConstantInt(x)
}
trait GE extends Product {
  private[fscape] def expand: Signal
}