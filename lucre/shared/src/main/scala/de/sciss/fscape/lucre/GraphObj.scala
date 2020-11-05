/*
 *  GraphObj.scala
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

package de.sciss.fscape
package lucre

import java.net.URI
import java.util

import de.sciss.fscape.graph.{Constant, ConstantD, ConstantI, ConstantL}
import de.sciss.lucre.Event.Targets
import de.sciss.lucre.impl.{DummyEvent, ExprTypeImpl}
import de.sciss.lucre.{Artifact, Copy, Elem, Event, EventLike, Expr, Ident, Obj, Txn, Var => LVar}
import de.sciss.model.Change
import de.sciss.serial.{ConstFormat, DataInput, DataOutput}

import scala.annotation.{switch, tailrec}
import scala.util.control.NonFatal

object GraphObj extends ExprTypeImpl[Graph, GraphObj] {
  final val typeId = 100

  def tryParse(value: Any): Option[Graph] = value match {
    case x: Graph => Some(x)
    case _        => None
  }

  protected def mkConst[T <: Txn[T]](id: Ident[T], value: A)(implicit tx: T): Const[T] =
    new _Const[T](id, value)

  protected def mkVar[T <: Txn[T]](targets: Targets[T], vr: LVar[T, E[T]], connect: Boolean)
                                  (implicit tx: T): Var[T] = {
    val res = new _Var[T](targets, vr)
    if (connect) res.connect()
    res
  }

  private final class _Const[T <: Txn[T]](val id: Ident[T], val constValue: A)
    extends ConstImpl[T] with GraphObj[T]

  private final class _Var[T <: Txn[T]](val targets: Targets[T], val ref: LVar[T, E[T]])
    extends VarImpl[T] with GraphObj[T]

  /** A format for graphs. */
  object valueFormat extends ConstFormat[Graph] {
    private final val SER_VERSION = 0x5347

    // we use an identity hash map, because we do _not_
    // want to alias objects in the serialization; the input
    // is an in-memory object graph.
    private type RefMapOut = util.IdentityHashMap[Product, Integer]

    private final class RefMapIn {
      var map   = Map.empty[Int, Product]
      var count = 0
    }

    private def writeProduct(p: Product, out: DataOutput, ref: RefMapOut): Unit = {
      val id0Ref = ref.get(p)
      if (id0Ref != null) {
        out.writeByte('<')
        out.writeInt(id0Ref)
        return
      }
      out.writeByte('P')
      val pck     = p.getClass.getPackage.getName
      val prefix  = p.productPrefix
      val name    = if (pck == "de.sciss.fscape.graph") prefix else s"$pck.$prefix"
      out.writeUTF(name)
      out.writeShort(p.productArity)
      p.productIterator.foreach(writeElem(_, out, ref))

      val id     = ref.size() // count
      ref.put(p, id)
      ()
    }

    private def writeElemSeq(xs: Seq[Any], out: DataOutput, ref: RefMapOut): Unit = {
      out.writeByte('X')
      out.writeInt(xs.size)
      xs.foreach(writeElem(_, out, ref))
    }

    @tailrec
    private def writeElem(e: Any, out: DataOutput, ref: RefMapOut): Unit =
      e match {
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
//        case r: MaybeRate =>
//          out.writeByte('R')
//          out.writeByte(r.id)
        case o: Option[_] =>
          out.writeByte('O')
          out.writeBoolean(o.isDefined)
          if (o.isDefined) writeElem(o.get, out, ref)
        case xs: Seq[_] =>  // 'X'. either indexed seq or var arg (e.g. wrapped array)
          writeElemSeq(xs, out, ref)
        case y: Graph =>  // important to handle Graph explicitly, as `apply` is overloaded!
          out.writeByte('Y')
          writeIdentifiedGraph(y, out, ref)
        // important: `Product` must come after all other types that might _also_ be a `Product`
        case p: Product =>
          writeProduct(p, out, ref) // 'P' or '<'
        case i: Int =>
          out.writeByte('I')
          out.writeInt(i)
        case s: String =>
          out.writeByte('S')
          out.writeUTF(s)
        case b: Boolean   =>
          out.writeByte('B')
          out.writeBoolean(b)
        case f: Float =>
          out.writeByte('F')
          out.writeFloat(f)
        case d: Double =>
          out.writeByte('D')
          out.writeDouble(d)
        case n: Long =>
          out.writeByte('L')
          out.writeLong(n)
        case _: Unit =>
          out.writeByte('U')
//        case f: File =>
        case u: URI =>
          out.writeByte('u')
          Artifact.Value.write(u, out)
      }

    def write(v: Graph, out: DataOutput): Unit = {
      out.writeShort(SER_VERSION)
      val ref = new RefMapOut
      writeIdentifiedGraph(v, out, ref)
    }

    private def writeIdentifiedGraph(v: Graph, out: DataOutput, ref: RefMapOut): Unit = {
      writeElemSeq(v.sources, out, ref)
//      val ctl = v.controlProxies
//      out.writeByte('T')
//      out.writeInt(ctl.size)
//      ctl.foreach(writeProduct(_, out, ref))
    }

    // expects that 'X' byte has already been read
    private def readIdentifiedSeq(in: DataInput, ref: RefMapIn): Seq[Any] = {
      val num = in.readInt()
      Vector.fill(num)(readElem(in, ref))
    }

    // expects that 'P' byte has already been read
    private def readIdentifiedProduct(in: DataInput, ref: RefMapIn): Product = {
      val prefix    = in.readUTF()
      val arity     = in.readShort()
      val className = if (Character.isUpperCase(prefix.charAt(0))) s"de.sciss.fscape.graph.$prefix" else prefix

      val res = try {
        if (arity == 0 && className.charAt(className.length - 1) == '$') {
          // case object
          val companion = Class.forName(s"$className").getField("MODULE$").get(null)
          companion.asInstanceOf[Product]

        } else {

          // cf. stackoverflow #3039822
          val companion = Class.forName(s"$className$$").getField("MODULE$").get(null)
          val elems = new Array[AnyRef](arity)
          var i = 0
          while (i < arity) {
            elems(i) = readElem(in, ref).asInstanceOf[AnyRef]
            i += 1
          }
          //    val m         = companion.getClass.getMethods.find(_.getName == "apply")
          //      .getOrElse(sys.error(s"No apply method found on $companion"))
          val ms = companion.getClass.getMethods
          var m = null: java.lang.reflect.Method
          var j = 0
          while (m == null && j < ms.length) {
            val mj = ms(j)
            if (mj.getName == "apply" && mj.getParameterTypes.length == arity) m = mj
            j += 1
          }
          if (m == null) sys.error(s"No apply method found on $companion")

          m.invoke(companion, elems: _*).asInstanceOf[Product]
        }

      } catch {
        case NonFatal(e) =>
          throw new IllegalArgumentException(s"While de-serializing $prefix", e)
      }

      val id        = ref.count
      ref.map      += ((id, res))
      ref.count     = id + 1
      res
    }

    private def readElem(in: DataInput, ref: RefMapIn): Any = {
      (in.readByte(): @switch) match {
        case 'C' =>
          (in.readByte(): @switch) match {
            case 'd' => ConstantD(in.readDouble())
            case 'i' => ConstantI(in.readInt   ())
            case 'l' => ConstantL(in.readLong  ())
          }
//        case 'R' => MaybeRate(in.readByte())
        case 'O' => if (in.readBoolean()) Some(readElem(in, ref)) else None
        case 'X' => readIdentifiedSeq(in, ref)
        case 'Y' => readIdentifiedGraph  (in, ref)
        case 'P' => readIdentifiedProduct(in, ref)
        case '<' =>
          val id = in.readInt()
          ref.map(id)
        case 'I' => in.readInt()
        case 'S' => in.readUTF()
        case 'B' => in.readBoolean()
        case 'F' => in.readFloat()
        case 'D' => in.readDouble()
        case 'L' => in.readLong()
        case 'U' => ()
        case 'u' =>
          Artifact.Value.read(in)
        case 'f' =>   // backwards compatible
          val path = in.readUTF()
          new URI("file", path, null)
      }
    }

    def read(in: DataInput): Graph = {
      val cookie  = in.readShort()
      require(cookie == SER_VERSION, s"Unexpected cookie $cookie")
      val ref = new RefMapIn
      readIdentifiedGraph(in, ref)
    }

    private def readIdentifiedGraph(in: DataInput, ref: RefMapIn): Graph = {
      val b1 = in.readByte()
      require(b1 == 'X')    // expecting sequence
      val numSources  = in.readInt()
      val sources     = Vector.fill(numSources) {
        readElem(in, ref).asInstanceOf[Lazy]
      }
//      val b2 = in.readByte()
//      require(b2 == 'T')    // expecting set
//      val numControls = in.readInt()
//      val controls    = Set.newBuilder[ControlProxyLike] // stupid Set doesn't have `fill` and `tabulate` methods
//      var i = 0
//      while (i < numControls) {
//        controls += readElem(in, ref).asInstanceOf[ControlProxyLike]
//        i += 1
//      }
      Graph(sources /* , controls.result() */)
    }
  }

  private final val emptyCookie = 4

  override protected def readCookie[T <: Txn[T]](in: DataInput, cookie: Byte)
                                                (implicit tx: T): E[T] =
    cookie match {
      case `emptyCookie` =>
        val id = tx.readId(in)
        new Predefined(id, cookie)
      case _ => super.readCookie(in, cookie)
    }

  private val emptyGraph = Graph {}

  def empty[T <: Txn[T]](implicit tx: T): E[T] = apply(emptyCookie  )

  private def apply[T <: Txn[T]](cookie: Int)(implicit tx: T): E[T] = {
    val id = tx.newId()
    new Predefined(id, cookie)
  }

  private final class Predefined[T <: Txn[T]](val id: Ident[T], cookie: Int)
    extends GraphObj[T] with Expr.Const[T, Graph] {

    def event(slot: Int): Event[T, Any] = throw new UnsupportedOperationException

    def tpe: Obj.Type = GraphObj

    def copy[Out <: Txn[Out]]()(implicit tx: T, txOut: Out, context: Copy[T, Out]): Elem[Out] =
      new Predefined(txOut.newId(), cookie) // .connect()

    def write(out: DataOutput): Unit = {
      out.writeInt(tpe.typeId)
      out.writeByte(cookie)
      id.write(out)
    }

    def value(implicit tx: T): Graph = constValue

    def changed: EventLike[T, Change[Graph]] = DummyEvent[T, Change[Graph]]

    def dispose()(implicit tx: T): Unit = ()

    def constValue: Graph = cookie match {
      case `emptyCookie` => emptyGraph
    }
  }
}
trait GraphObj[T <: Txn[T]] extends Expr[T, Graph]