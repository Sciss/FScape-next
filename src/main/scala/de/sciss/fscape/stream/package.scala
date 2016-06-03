package de.sciss.fscape

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import akka.NotUsed
import akka.stream.{Inlet, Outlet}
import akka.stream.scaladsl.{FlowOps, GraphDSL}

import scala.annotation.elidable
import scala.annotation.elidable._
import scala.language.implicitConversions

package object stream {
  type Signal[A] = FlowOps[A, NotUsed]

//  // to-do: `unfold` is unnecessarily inefficient because of producing `Option[Int]`.
//  implicit def constIntSignal   (i: Int   ): Signal[Int]    = Source.repeat(i) // or better `single`?
//  implicit def constDoubleSignal(d: Double): Signal[Double] = Source.repeat(d) // or better `single`?

  type InI                     = Inlet[BufI]
  type InD                     = Inlet[BufD]

  @inline
  def  InI(name: String): InI = Inlet[BufI](name)
  @inline
  def  InD(name: String): InD = Inlet[BufD](name)

  type OutI                     = Outlet[BufI]
  type OutD                     = Outlet[BufD]

  @inline
  def  OutI(name: String): OutI = Outlet[BufI](name)
  @inline
  def  OutD(name: String): OutD = Outlet[BufD](name)

  type GBuilder = GraphDSL.Builder[NotUsed]

  private lazy val logHeader = new SimpleDateFormat("[d MMM yyyy, HH:mm''ss.SSS] 'fscape' - ", Locale.US)
  var showStreamLog = false

  @elidable(CONFIG) private[stream] def logStream(what: => String): Unit =
    if (showStreamLog) Console.out.println(s"${logHeader.format(new Date())}stream $what")

}