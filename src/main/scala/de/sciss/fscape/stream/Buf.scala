package de.sciss.fscape.stream

final class BufD(val buf: Array[Double], @volatile var size: Int, val borrowed: Boolean)

object BufI {
  def apply(elems: Int*): BufI = {
    val arr = elems.toArray
    new BufI(arr, size = arr.length, borrowed = false)
  }
}
final class BufI(val buf: Array[Int]   , @volatile var size: Int, val borrowed: Boolean)
