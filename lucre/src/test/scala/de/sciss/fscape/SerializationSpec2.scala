package de.sciss.fscape

import de.sciss.fscape.lucre.GraphObj
import de.sciss.serial.{DataInput, DataOutput}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SerializationSpec2 extends AnyFlatSpec with Matchers {
  var dfs = Map.empty[String, Graph]

  import graph._
  import lucre.graph._
  import Ops._

  dfs += "graph 1" -> Graph {
    val fun = "fun".attr(0)
    val sig = If (fun sig_== 0) Then {
      SinOsc(1.0/100)
    } ElseIf (fun sig_== 1) Then {
      LFSaw(1.0/100)
    } Else {
      Impulse(1.0/100)
    }
    Plot1D(sig, size = 400)
  }

  dfs.foreach { case (n, g) =>
    s"Example $n" should "be serializable" in {
      import GraphObj.{valueSerializer => ser}
      val out = DataOutput()
      ser.write(g, out)
      val in = DataInput(out.toByteArray)
      val gT = ser.read(in)
      assert (g == gT)
    }
  }
}
