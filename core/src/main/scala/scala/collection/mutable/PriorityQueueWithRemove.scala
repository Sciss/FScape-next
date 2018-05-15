// here because `RedBlackTree` is package-private ...
package scala.collection.mutable

import scala.collection.mutable.{RedBlackTree => RB}

final class PriorityQueueWithRemove[A](implicit ord: Ordering[A]) {
  
  private[this] val tree  = RB.Tree.empty[A, Int]
  private[this] var _size = 0 

  def add(d: A): Boolean = {
    _size += 1
    RB.get(tree, d)(ord) match {
      case Some(c) =>
        RB.insert(tree, d, c + 1)(ord)
        false

      case None =>
        RB.insert(tree, d, 1)(ord)
        true
    }
  }

  def remove(d: A): Boolean =
    RB.get(tree, d)(ord) match {
      case Some(c) =>
        if (c == 1) RB.delete(tree, d)
        else        RB.insert(tree, d, c - 1)
        _size -= 1
        true

      case None =>
        false
    }

  def size    : Int     = _size
  def isEmpty : Boolean = _size == 0
  def nonEmpty: Boolean = _size != 0

  def max: A = RB.maxKey(tree).get
  def min: A = RB.minKey(tree).get

  def removeMax(): A = { val d = max; remove(d); d }
  def removeMin(): A = { val d = min; remove(d); d }
}
