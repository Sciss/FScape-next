package de.sciss.fscape

import de.sciss.file._

trait UtilPlatform {
  /** If the input contains placeholder `%`, returns it unchanged,
    * otherwise determines an integer number in the name and replaces
    * it by `%d`.
    */
  def mkTemplate(in: File): File = {
    val n = in.name
    if (n.contains("%")) in else {
      val j = n.lastIndexWhere(_.isDigit)
      if (j < 0) throw new IllegalArgumentException(
        s"Cannot make template out of file name '$n'. Must contain '%d' or integer number.")

      var i = j
      while (i > 0 && n.charAt(i - 1).isDigit) i -= 1
      val pre   = n.substring(0, i)
      val post  = n.substring(j + 1)
      val nn    = s"$pre%d$post"
      in.replaceName(nn)
    }
  }
}
