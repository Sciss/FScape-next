package de.sciss.fscape.stream

trait BufLike {
  def release()(implicit ctrl: Control): Unit

  /* @volatile */ var size: Int
}

object BufD {
  def apply(elems: Double*): BufD = {
    val arr = elems.toArray
    new BufD(arr, size = arr.length, borrowed = false)
  }
}
final class BufD(val buf: Array[Double], @volatile var size: Int, val borrowed: Boolean) extends BufLike {
  def release()(implicit ctrl: Control): Unit = ctrl.returnBufD(this)
}

object BufI {
  def apply(elems: Int*): BufI = {
    val arr = elems.toArray
    new BufI(arr, size = arr.length, borrowed = false)
  }
}
final class BufI(val buf: Array[Int], @volatile var size: Int, val borrowed: Boolean) extends BufLike {
  def release()(implicit ctrl: Control): Unit = ctrl.returnBufI(this)
}
