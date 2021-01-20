/*
 *  Graph.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape

import de.sciss.fscape.graph.{Constant, ConstantD, ConstantI, ConstantL}
import de.sciss.serial
import de.sciss.serial.{DataInput, DataOutput}

import java.net.URI
import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.collection.mutable

object Graph {
  trait Builder {
    def addLazy(x: Lazy): Unit

    def removeLazy(x: Lazy): Unit
  }

  /** This is analogous to `SynthGraph.Builder` in ScalaCollider. */
  def builder: Builder  = builderRef.get()

  /** Installs a custom graph builder on the current thread,
    * during the invocation of a closure. This method is typically
    * called from other libraries which wish to provide a graph
    * builder other than the default.
    *
    * When the method returns, the previous graph builder has automatically
    * been restored. During the execution of the `body`, calling
    * `Graph.builder` will return the given `builder` argument.
    *
    * @param builder    the builder to install on the current thread
    * @param body       the body which is executed with the builder found through `Graph.builder`
    * @tparam A         the result type of the body
    * @return           the result of executing the body
    */
  def use[A](builder: Builder)(body: => A): A = {
    val old = builderRef.get()
    builderRef.set(builder)
    try {
      body
    } finally {
      builderRef.set(old)
    }
  }

  private[this] val builderRef: ThreadLocal[Builder] = new ThreadLocal[Builder] {
    override protected def initialValue: Builder = BuilderDummy
  }

  private[this] object BuilderDummy extends Builder {
    def addLazy   (x: Lazy): Unit = ()
    def removeLazy(x: Lazy): Unit = ()
  }

  def apply(thunk: => Any): Graph = {
    val b   = new BuilderImpl
    val old = builderRef.get()
    builderRef.set(b)
    try {
      thunk
      b.build
    } finally {
      builderRef.set(old) // BuilderDummy
    }
  }

  trait ProductReader[+A] {
    def read(in: RefMapIn, key: String, arity: Int): A
  }

  private val mapRead = mutable.Map.empty[String, ProductReader[Product]]

  final val DefaultPackage = "de.sciss.fscape.graph"

  /** Derives the `productPrefix` served by the reader by the reader's class name itself.  */
  def addProductReaderSq(xs: Iterable[ProductReader[Product]]): Unit = {
    val m = mapRead
    m.synchronized {
      xs.foreach { value =>
        val cn    = value.getClass.getName
        val nm    = cn.length - 1
        val isObj = cn.charAt(nm) == '$'
        val j     = cn.lastIndexOf('.')
        val pkg   = cn.substring(0, j)
        val i     = if (pkg == DefaultPackage) DefaultPackage.length + 1 else 0
        val key   = if (isObj) cn.substring(i, nm) else cn.substring(i)
        m += ((key, value))
      }
    }
  }

  private final val URI_SER_VERSION = 2

  final class RefMapOut(out0: DataOutput) extends serial.RefMapOut(out0) {
    override protected def isDefaultPackage(pck: String): Boolean =
      pck == DefaultPackage

    override def writeElem(e: Any): Unit = e match {
      case c: Constant =>
        out.writeByte('C')
        if (c.isDouble) {
          out.writeByte('d')
          out.writeDouble(c.doubleValue)
        } else if (c.isInt) {
          out.writeByte('i')
          out.writeInt(c.intValue)
        } else if (c.isLong) {
          out.writeByte('l')
          out.writeLong(c.longValue)
        }

      case y: Graph =>  // important to handle Graph explicitly, as `apply` is overloaded!
        out.writeByte('Y')
        writeIdentifiedGraph(y)

      case _ => super.writeElem(e)
    }

    def writeIdentifiedGraph(y: Graph): Unit =
      writeVec(y.sources, writeElem)

    override protected def writeCustomElem(e: Any): Any =
      e match {
        //    case f: File =>
        //      out.writeByte('f')
        //      out.writeUTF(f.getPath)
        //      ref0

        case u: URI =>
          out.writeByte('u')
          out.writeByte(URI_SER_VERSION)
          out.writeUTF(u.toString)

        case _ => super.writeCustomElem(e)
      }
  }

  final class RefMapIn(in0: DataInput) extends serial.RefMapIn[RefMapIn](in0) {
    type Const  = Constant
    type Y      = Graph
    type U      = URI

    override protected def readProductWithKey(key: String, arity: Int): Product = {
      val r = mapRead.getOrElse(key, throw new NoSuchElementException(s"Unknown element '$key'"))
      r.read(this, key, arity)
    }

    override protected def readIdentifiedConst(): Constant =
      (in.readByte().toChar: @switch) match {
        case 'd' => ConstantD(in.readDouble())
        case 'i' => ConstantI(in.readInt())
        case 'l' => ConstantL(in.readLong())
      }

//    override protected def readCustomElem(cookie: Char): Any =
//      if (cookie == 'f') { // backwards compatibility
//        val path = in.readUTF()
//        fileToURI(path)
//      } else {
//        super.readCustomElem(cookie)
//      }

    def readURI(): URI = {
      val cookie = in0.readByte().toChar
      if (cookie != 'u') unexpectedCookie(cookie, 'u')
      readIdentifiedU()
    }

    override protected def readIdentifiedU(): U = {
//      Artifact.Value.read(in)
      // XXX TODO: copy from Lucre. Not nice, but we do not want to depend on it in `core`
      val ver = in.readByte()
      if (ver != URI_SER_VERSION) {
//        if (ver == 1) { // old school plain path
//          val filePath = in.readUTF()
//          return fileToURI(filePath)
//        }
        sys.error(s"Unexpected serialization version ($ver != $URI_SER_VERSION)")
      }
      val str = in.readUTF()
      if (str.isEmpty) /*Value.empty*/ new URI(null, "", null) else new URI(str)
    }

    override def readIdentifiedY(): Graph = readIdentifiedGraph()

    def readGraph(): Graph = {
      val cookie = in0.readByte().toChar
      if (cookie != 'Y') unexpectedCookie(cookie, 'Y')
      readIdentifiedGraph()
    }

    def readIdentifiedGraph(): Graph = {
      val sources = readVec(readProductT[Lazy]())
      Graph(sources /* , controls.result() */)
    }

    def readGE(): GE =
      readProduct().asInstanceOf[GE]
  }

  private[this] final class BuilderImpl extends Builder {
    private var lazies = Vector.empty[Lazy]

    override def toString = s"fscape.Graph.Builder@${hashCode.toHexString}"

    def build: Graph = Graph(lazies)

    def addLazy(g: Lazy): Unit = lazies :+= g

    def removeLazy(g: Lazy): Unit =
      lazies = if (lazies.last == g) lazies.init else lazies.filterNot(_ == g)
  }
}

final case class Graph(sources: Vec[Lazy] /* , controlProxies: Set[ControlProxyLike] */) {
  def isEmpty : Boolean  = sources.isEmpty // && controlProxies.isEmpty
  def nonEmpty: Boolean  = !isEmpty

  // def expand(implicit ctrl: stream.Control): UGenGraph = UGenGraph.build(this)
}

