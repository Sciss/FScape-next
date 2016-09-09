//package de.sciss.fscape.stream
//
//import java.util.concurrent.atomic.AtomicBoolean
//
//import akka.stream.Attributes
//
//object Leaves {
//  private val tl = new ThreadLocal[Leaves]
//
//  def get: Leaves = {
//    val res = tl.get()
//    if (res == null) throw new IllegalStateException("Expanding UGens outside of Control")
//    res
//  }
//
//  def use[A](l: Leaves)(thunk: => A): A = {
//    val old = tl.get()
//    try {
//      tl.set(l)
//      thunk
//    } finally {
//      tl.set(old)
//    }
//  }
//}
//final class Leaves extends Attributes.Attribute {
//  private[this] val begun = new AtomicBoolean(false)
//
//  @volatile
//  private[this] var completed = false
//
//  @volatile
//  private[this] var list = List.empty[Leaf]
//
//  def add(l: Leaf): Unit = {
//    list ::= l
//    require(!completed)
//  }
//
//  def begin(): Unit = {
//    val old = begun.getAndSet(true)
//    require(!old, "Leaves: begin was called repeatedly")
//  }
//
//  def result(): List[Leaf] = {
//    require(begun.get(), "Leaves: was not populated yet")
//    completed = true
//    list.reverse
//  }
//}