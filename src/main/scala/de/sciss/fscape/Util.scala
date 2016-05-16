package de.sciss.fscape

object Util {
  def copy(in: Array[Array[Float]], inOff: Int, out: Array[Array[Double]], outOff: Int, len: Int): Unit = {
    var ch = 0
    while (ch < in.length) {
      var i     = inOff
      val stop  = i + len
      var j     = outOff
      val a     = in(ch)
      val b     = out(ch)
      while (i < stop) {
        b(j) = a(i)
        i += 1
        j += 1
      }
      ch += 1
    }
  }

  def copy(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit =
    System.arraycopy(in, inOff, out, outOff, len)

  def fill(out: Array[Array[Double]], off: Int, len: Int, value: Double): Unit = {
    var ch = 0
    while (ch < out.length) {
      var i     = off
      val stop  = i + len
      val a     = out(ch)
      while (i < stop) {
        a(i) = value
        i += 1
      }
      ch += 1
    }
  }

  def fill(out: Array[Double], off: Int, len: Int, value: Double): Unit = {
    var i     = off
    val stop  = i + len
    while (i < stop) {
      out(i) = value
      i += 1
    }
  }
}
