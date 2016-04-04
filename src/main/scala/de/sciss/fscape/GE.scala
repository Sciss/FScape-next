/*
 *  GE.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape

import scala.language.implicitConversions

trait Lazy extends Product {
  private[fscape] def force(b: Module.Builder): Unit
}

trait Expander[+U] extends Lazy {
  // this acts now as a fast unique reference
  @transient final private[this] lazy val ref = new AnyRef

  // ---- constructor ----
  Graph.builder.addLazy(this)

  /** A final implementation of this method which calls `visit` on the builder,
    * checking if this element has already been visited, and if not, will invoke
    * the `expand` method. Therefore it is guaranteed, that the expansion to
    * ugens is performed no more than once in the graph expansion.
    */
  final private[fscape] def force(b: Module.Builder): Unit = visit(b)

  /** A final implementation of this method which looks up the current ugen graph
    * builder and then performs the expansion just as `force`, returning the
    * expanded object
    *
    * @return  the expanded object (e.g. `Unit` for a ugen with no outputs,
    *          or a single ugen, or a group of ugens)
    */
  final private[fscape] def expand: U = visit(Module.builder)

  private def visit(b: Module.Builder): U = b.visit(ref, makeSignal)

  /** Abstract method which must be implemented by creating the actual `UGen`s
    * during expansion. This method is at most called once during graph
    * expansion
    *
    * @return  the expanded object (depending on the type parameter `U`)
    */
  protected def makeSignal: U
}

object UGenSource {
  trait ZeroOut   extends UGenSource[Unit]
  trait SingleOut extends UGenSource[UGenIn] with GE
}
/** This is analogous to `UGenSource` in ScalaCollider. */
trait UGenSource[+U] extends Expander[U] {
}

object GE {
  trait Lazy extends Expander[UGenIn] with GE

  implicit def fromInt(x: Int): ConstantInt = new ConstantInt(x)
}
trait GE extends Product {
  private[fscape] def expand: UGenIn
}