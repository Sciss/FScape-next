package de.sciss.fscape.stream

import akka.stream.{Attributes, BidiShape, FanInShape3, Inlet, Outlet}
import de.sciss.fscape.stream.impl.Handlers.{InIAux, InIMain, OutIMain}
import de.sciss.fscape.stream.impl.{Handlers, NodeImpl, StageImpl}
import de.sciss.fscape.Log.{stream => logStream}

import scala.annotation.tailrec
import scala.math.{max, min}

object HilbertCurve {
  def From2D(n: OutI, x: OutI, y: OutI)(implicit b: Builder): OutI = {
    val stage0  = new StageFrom2D(b.layer)
    val stage   = b.add(stage0)
    b.connect(n, stage.in0)
    b.connect(x, stage.in1)
    b.connect(y, stage.in2)
    stage.out
  }

  private final val nameFrom2D = "HilbertCurve.From2D"

  private type ShpFrom2D = FanInShape3[BufI, BufI, BufI, BufI]

  private final class StageFrom2D(layer: Layer)(implicit ctrl: Control) extends StageImpl[ShpFrom2D](nameFrom2D) {
    val shape: Shape = new FanInShape3(
      in0 = InI (s"$name.n"   ),
      in1 = InI (s"$name.x"   ),
      in2 = InI (s"$name.y"   ),
      out = OutI(s"$name.out" )
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new LogicFrom2D(shape, layer)
  }

  private final class LogicFrom2D(shape: ShpFrom2D, layer: Layer)(implicit control: Control)
    extends Handlers(nameFrom2D, layer, shape) {

    private[this] val hX      : InIMain   = InIMain  (this, shape.in1)
    private[this] val hY      : InIMain   = InIMain  (this, shape.in2)
    private[this] val hN      : InIAux    = InIAux   (this, shape.in0)(math.max(1, _))
    private[this] val hOut    : OutIMain  = OutIMain (this, shape.out)

    private def checkDone(): Boolean =
      if ((hX.isDone || hY.isDone) && hOut.flush()) {
        completeStage()
        true
      } else {
        false
      }

    protected def onDone(inlet: Inlet[_]): Unit = {
      checkDone()
      ()
    }

    @tailrec
    protected def process(): Unit = {
      logStream.debug(s"$this process()")

      val _hN   = hN
      val _hX   = hX
      val _hY   = hY
      val remIn = min(_hN.available, min(_hX.available, _hY.available))
      if (remIn == 0) return

      val _hOut = hOut
      val remOut = _hOut.available
      if (remOut == 0) return
      val rem = min(remIn, remOut)

      var i = 0
      while (i < rem) {
        val n   = _hN.next()
        val n1  = n - 1
        var x   = max(0, min(n1, _hX.next()))
        var y   = max(0, min(n1, _hY.next()))
        var d   = 0
        var s   = n >> 1
        while (s > 0) {
          val rx    = (x & s) > 0
          val ryn   = (y & s) == 0
          val rxi3  = if (rx) 3 else 0
          val ryi   = if (ryn) 0 else 1
          d += s * s * (rxi3 ^ ryi)

          if (ryn) {
            if (rx) {
              x = n1 - x
              y = n1 - y
            }

            val t = x
            x = y
            y = t
          }

          s >>= 1
        }

        _hOut.next(d)
        i += 1
      }

      if (!checkDone()) process()
    }
  }
  
  // ------------------

  def To2D(n: OutI, pos: OutI)(implicit b: Builder): (OutI, OutI) = {
    val stage0  = new StageTo2D(b.layer)
    val stage   = b.add(stage0)
    b.connect(n   , stage.in1)
    b.connect(pos , stage.in2)
    (stage.out1, stage.out2)
  }

  private final val nameTo2D = "HilbertCurve.To2D"

  private type ShpTo2D = BidiShape[BufI, BufI, BufI, BufI]

  private final class StageTo2D(layer: Layer)(implicit ctrl: Control) extends StageImpl[ShpTo2D](nameTo2D) {
    val shape: Shape = new BidiShape(
      in1   = InI (s"$name.n"   ),
      in2   = InI (s"$name.pos" ),
      out1  = OutI(s"$name.x"   ),
      out2  = OutI(s"$name.y"   ),
    )

    def createLogic(attr: Attributes): NodeImpl[Shape] = new LogicTo2D(shape, layer)
  }

  private final class LogicTo2D(shape: ShpTo2D, layer: Layer)(implicit control: Control)
    extends Handlers(nameTo2D, layer, shape) {

    private[this] val hPos    : InIMain   = InIMain  (this, shape.in2)
    private[this] val hN      : InIAux    = InIAux   (this, shape.in1)(math.max(1, _))
    private[this] val hOutX   : OutIMain  = OutIMain (this, shape.out1)
    private[this] val hOutY   : OutIMain  = OutIMain (this, shape.out2)

    private def checkDone(): Boolean =
      if (hPos.isDone && hOutX.flush() && hOutY.flush()) {
        completeStage()
        true
      } else {
        false
      }

    protected def onDone(inlet: Inlet[_]): Unit = {
      checkDone()
      ()
    }

    override protected def onDone(outlet: Outlet[_]): Unit =
      if (hOutX.isDone && hOutY.isDone) {
        completeStage()
      } else if (!checkDone()) {
        process()
      }

    @tailrec
    protected def process(): Unit = {
      logStream.debug(s"$this process()")

      val _hN     = hN
      val _hPos   = hPos
      val remIn = min(_hN.available, _hPos.available)
      if (remIn == 0) return

      val _hX     = hOutX
      val _hY     = hOutY
      val hasX    = !_hX.isDone
      val hasY    = !_hY.isDone
      val remOut  = if (!hasY) {
        _hX.available
      } else if (!hasX) {
        _hY.available
      } else {
        min(_hX.available, _hY.available)
      }
      if (remOut == 0) return
      val rem = min(remIn, remOut)

      var i = 0
      while (i < rem) {
        val n   = _hN.next()
        val nn  = n * n
        var pos = max(0, min(nn - 1, _hPos.next()))

        var x = 0
        var y = 0
        var s = 1
        while (s < n) {
          val rxi = 1 & (pos >> 1)
          val ryi = 1 & (pos ^ rxi)

          if (ryi == 0) {
            if (rxi == 1) {
              val s1  = s - 1
              x = s1 - x
              y = s1 - y
            }
            val t = x
            x = y
            y = t
          }

          x += s * rxi
          y += s * ryi
          pos >>= 2
          s <<= 1
        }

        if (hasX) _hX.next(x)
        if (hasY) _hY.next(y)

        i += 1
      }

      if (!checkDone()) process()
    }
  }
}
