package de.sciss.fscape

object DEnvGenTest extends App {
  val g = Graph {
    import graph._
    val levels    = Seq[GE](0.0, -1.0, 1.0, 0.1)
    val lengths   = Seq       (100, 200, 50)
    val lengthsG  = lengths.map(x => x: GE)
    val shapes    = Seq[GE]    (1,   3,   2)    // lin, sine, exp

    val env       = DEnvGen(
      levels  = levels  .reduce(_ ++ _),
      lengths = lengthsG.reduce(_ ++ _),
      shapes  = shapes  .reduce(_ ++ _))

    Length(env).poll(0, "length")

    Plot1D(env, size = lengths.sum, label = "env")
  }

  stream.Control().run(g)
}