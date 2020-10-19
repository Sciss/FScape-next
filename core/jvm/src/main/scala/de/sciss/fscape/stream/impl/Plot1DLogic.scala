/*
 *  Plot1DLogic.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape.stream.impl

import akka.stream.Inlet
import de.sciss.fscape.logStream
import de.sciss.fscape.stream.impl.shapes.SinkShape2
import de.sciss.fscape.stream.{BufElem, BufI, Control, Layer, StreamType}

import scala.annotation.tailrec
import scala.math.{max, min}
import scala.swing.{BorderPanel, BoxPanel, Button, Component, Frame, Label, Orientation, Swing}

abstract class Plot1DLogic[A, E <: BufElem[A]](name: String, shape: SinkShape2[E, BufI], layer: Layer, label: String)
                                             (implicit ctrl: Control, tpe: StreamType[A, E])
  extends Handlers(name, layer, shape) {

  override def toString = s"$name-L($label)"

  protected def processWindow(data: Array[A], num: Int, framesRead: Long, hasGUI: Boolean): Unit

  protected def panel: Component

  private[this] val hIn   = Handlers.InMain[A, E] (this, shape.in0)
  private[this] val hSize = Handlers.InIAux       (this, shape.in1)(max(0, _))

  private[this] var winSize = -1
  private[this] var winBuf: Array[A] = _

  private[this] var framesRead        = 0L

  private[this] var writeToWinOff     = 0
  private[this] var writeToWinRemain  = 0
  private[this] var isNextWindow      = true

  private[this] var canWriteToWindow  = true  // false while plot is displayed
  private[this] var hasGUI            = false

  // ---- gui ----

  protected final lazy val frame: Frame = new Frame {
    title     = label
    contents  = allPanel
    pack()
    centerOnScreen()

    override def closeOperation(): Unit =
      async(completeStage())
  }

  // ---- methods ----

  override protected def stopped(): Unit = {
    super.stopped()
    if (hasGUI) Swing.onEDT {
      endedGUI()
    }
  }

  private[this] lazy val lbInfo = new Label()

  private[this] lazy val ggNext = Button("Next") {
    async {
      allowNext()
    }
  }

  private[this] lazy val pBottom: Component = {
    val pBot = new BoxPanel(Orientation.Horizontal)
    pBot.contents += Swing.HStrut(4)
    pBot.contents += lbInfo
    pBot.contents += Swing.HGlue
    pBot.contents += ggNext
    pBot
  }

  private[this] lazy val allPanel: Component = {
    val p = new BorderPanel
    p.layout(panel)   = BorderPanel.Position.Center
    p.layout(pBottom) = BorderPanel.Position.South
    p.layoutManager.setVgap(2)
    p
  }

  private def endedGUI(): Unit = {
    lbInfo.text += "  (ended)"
    ggNext.enabled = false
  }

  private def shouldComplete(): Boolean = hIn.isDone && writeToWinOff == 0

  protected def onDone(inlet: Inlet[_]): Unit =
    process()

  protected final def allowNext(): Unit = {
    canWriteToWindow = true
    process()
  }

  @tailrec
  protected final def process(): Unit = {
    logStream(s"process() $this")

    if (isNextWindow) {
      if (!hSize.hasNext) return
      val sz = hSize.next()
      if (sz == 0 && hSize.isConstant) completeStage()
      if (winSize != sz) {
        winBuf  = tpe.newArray(sz)
        winSize = sz
      }
      writeToWinRemain  = sz
      isNextWindow      = false
    }

    val rem = hIn.available
    if (rem == 0 || !canWriteToWindow) return
    processChunk(rem)

    if (shouldComplete()) {
      logStream(s"completeStage() $this")
      completeStage()
    } else {
      process()
    }
  }

  private def processChunk(rem: Int): Unit  = {
    val chunk = min(writeToWinRemain, rem)
    if (chunk > 0) {
      hIn.nextN(winBuf, writeToWinOff, chunk)
      writeToWinOff    += chunk
      writeToWinRemain -= chunk
    }

    if (writeToWinRemain == 0 || hIn.isDone) {
      assert (canWriteToWindow)
      val _hasGUI = hasGUI
      hasGUI            = true
      canWriteToWindow  = false
      val pos           = framesRead
      val num           = writeToWinOff
      processWindow(winBuf, num = num, framesRead = pos, hasGUI = _hasGUI)
      Swing.onEDT {
        lbInfo.text = s"x0 = $pos, n = $num"
      }
      framesRead       += writeToWinOff
      writeToWinOff     = 0
      isNextWindow      = true
    }
  }
}