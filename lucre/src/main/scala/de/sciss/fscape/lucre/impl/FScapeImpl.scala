/*
 *  FScapeImpl.scala
 *  (FScape)
 *
 *  Copyright (c) 2001-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape
package lucre
package impl

import de.sciss.fscape.lucre.FScape.{Output, Rendering}
import de.sciss.fscape.stream.Control
import de.sciss.lucre.data.SkipList
import de.sciss.lucre.event.Targets
import de.sciss.lucre.stm.impl.ObjSerializer
import de.sciss.lucre.stm.{Copy, Elem, NoSys, Obj, Sys}
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.lucre.{event => evt}
import de.sciss.serial.{DataInput, DataOutput, Serializer}
import de.sciss.synth.proc.{GenView, Universe}

import scala.collection.immutable.{IndexedSeq => Vec}

object FScapeImpl {
  private final val SER_VERSION_OLD = 0x4673  // "Fs"
  private final val SER_VERSION     = 0x4674

  def apply[S <: Sys[S]](implicit tx: S#Tx): FScape[S] = new New[S]

  def read[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): FScape[S] =
    serializer[S].read(in, access)

  def serializer[S <: Sys[S]]: Serializer[S#Tx, S#Acc, FScape[S]] = anySer.asInstanceOf[Ser[S]]

  private val anySer = new Ser[NoSys]

  private class Ser[S <: Sys[S]] extends ObjSerializer[S, FScape[S]] {
    def tpe: Obj.Type = FScape
  }

  def readIdentifiedObj[S <: Sys[S]](in: DataInput, access: S#Acc)(implicit tx: S#Tx): FScape[S] = {
    val targets = Targets.read(in, access)
    val serVer  = in.readShort()
    if (serVer == SER_VERSION) {
      new Read(in, access, targets)
    } else if (serVer == SER_VERSION_OLD) {
      new ReadOLD(in, access, targets)
    } else {
      sys.error(s"Incompatible serialized (found $serVer, required $SER_VERSION)")
    }
  }

//  // ---- Code ----
//
//  implicit object CodeWrapper extends CodeImpl.Wrapper[Unit, Graph, Unit, FScape.Code] {
//    def id      : Int             = FScape.Code.id
//    def binding : Option[String]  = None
//
//    def wrap(in: Unit)(fun: => Unit): Graph = Graph(fun)
//
//    def blockTag = "Unit"
//  }

  // ---- GenView ----

  def genViewFactory(config: Control.Config): GenView.Factory = new OutputGenViewFactory(config)

  private final class OutputGenViewFactory(config: Control.Config) extends GenView.Factory {
    def typeId: Int = Output.typeId

    type Repr[~ <: Sys[~]] = Output[~]

    def apply[S <: SSys[S]](output: Output[S])(implicit tx: S#Tx, universe: Universe[S]): GenView[S] = {
      val _fscape = output.fscape
      import universe.genContext
      val fscView = genContext.acquire[Rendering[S]](_fscape) {
        RenderingImpl(_fscape, config, force = false)
      }
      OutputGenView(config, output, fscView)
    }
  }

  private sealed trait Base[S <: Sys[S]] {
    _: FScape[S] =>

    final def tpe: Obj.Type = FScape

    override def toString: String = s"FScape$id"

    // --- rendering ---

    final def run(config: Control.Config)(implicit tx: S#Tx, universe: Universe[S]): Rendering[S] =
      Rendering(this, config)
  }

  private sealed trait ImplOLD[S <: Sys[S]]
    extends FScape[S] with Base[S] with evt.impl.SingleNode[S, FScape.Update[S]] {
    proc =>

    def copy[Out <: Sys[Out]]()(implicit tx: S#Tx, txOut: Out#Tx, context: Copy[S, Out]): Elem[Out] =
      new Impl[Out] { out =>
        protected val targets: Targets[Out] = Targets[Out]
        val graph     : GraphObj.Var[Out]                       = context(proc.graph)
        val outputsMap: SkipList.Map[Out, String, Output[Out]]  = SkipList.Map.empty
        connect()
      }

    import FScape._

    // ---- key maps ----

    def isConnected(implicit tx: S#Tx): Boolean = targets.nonEmpty

    final def connect()(implicit tx: S#Tx): this.type = {
      graph.changed ---> changed
      this
    }

    private def disconnect()(implicit tx: S#Tx): Unit = {
      graph.changed -/-> changed
    }

    object changed extends Changed
      with evt.impl.Generator[S, FScape.Update[S]] {
      def pullUpdate(pull: evt.Pull[S])(implicit tx: S#Tx): Option[FScape.Update[S]] = {
        val graphCh     = graph.changed
        val graphOpt    = if (pull.contains(graphCh)) pull(graphCh) else None
        val stateOpt    = Option.empty[FScape.Update[S]] // if (pull.isOrigin(this)) Some(pull.resolve[FScape.Update[S]]) else None

        val seq0 = graphOpt.fold(Vec.empty[Change[S]]) { u =>
          Vector(GraphChange(u))
        }

        val seq3 = stateOpt.fold(seq0) { u =>
          if (seq0.isEmpty) u.changes else seq0 ++ u.changes
        }
        if (seq3.isEmpty) None else Some(FScape.Update(proc, seq3))
      }
    }

    final protected def writeData(out: DataOutput): Unit = {
      out.writeShort(SER_VERSION_OLD)
      graph.write(out)
    }

    final protected def disposeData()(implicit tx: S#Tx): Unit = {
      disconnect()
      graph.dispose()
    }

    // dummy
    object outputs extends Outputs[S] {
      def get(key: String)(implicit tx: S#Tx): Option[Output[S]] = None

      def keys(implicit tx: S#Tx): Set[String] = Set.empty

      def iterator(implicit tx: S#Tx): Iterator[Output[S]] = Iterator.empty

      def add(key: String, tpe: Obj.Type)(implicit tx: S#Tx): Output[S] =
        throw new UnsupportedOperationException(s"Old FScape object without actual outputs, cannot call `add`")

      def remove(key: String)(implicit tx: S#Tx): Boolean = false
    }
  }

  private sealed trait Impl[S <: Sys[S]]
    extends FScape[S] with Base[S] with evt.impl.SingleNode[S, FScape.Update[S]] {
    proc =>

    // --- abstract ----

    protected def outputsMap: SkipList.Map[S, String, Output[S]]

    // --- impl ----

    def copy[Out <: Sys[Out]]()(implicit tx: S#Tx, txOut: Out#Tx, context: Copy[S, Out]): Elem[Out] =
      new Impl[Out] { out =>
        protected val targets: Targets[Out]                     = Targets[Out]
        val graph     : GraphObj.Var[Out]                       = context(proc.graph)
        val outputsMap: SkipList.Map[Out, String, Output[Out]]  = SkipList.Map.empty

        context.defer(proc, out) {
          def copyMap(in : SkipList.Map[S  , String, Output[S  ]],
                      out: SkipList.Map[Out, String, Output[Out]]): Unit =
            in.iterator.foreach { case (key, eIn) =>
              val eOut = context(eIn)
              out.put(key, eOut)
            }

          // copyMap(proc.scanInMap , out.scanInMap)
          copyMap(proc.outputsMap, out.outputsMap)
        }
        connect()
      }

    import FScape._

    // ---- key maps ----

    def isConnected(implicit tx: S#Tx): Boolean = targets.nonEmpty

    final def connect()(implicit tx: S#Tx): this.type = {
      graph.changed ---> changed
      this
    }

    private def disconnect()(implicit tx: S#Tx): Unit = {
      graph.changed -/-> changed
    }

    object changed extends Changed
      with evt.impl.Generator[S, FScape.Update[S]] {
      def pullUpdate(pull: evt.Pull[S])(implicit tx: S#Tx): Option[FScape.Update[S]] = {
        val graphCh     = graph.changed
        val graphOpt    = if (pull.contains(graphCh)) pull(graphCh) else None
        val stateOpt    = if (pull.isOrigin(this)) Some(pull.resolve[FScape.Update[S]]) else None

        val seq0 = graphOpt.fold(Vec.empty[Change[S]]) { u =>
          Vector(GraphChange(u))
        }

        val seq3 = stateOpt.fold(seq0) { u =>
          if (seq0.isEmpty) u.changes else seq0 ++ u.changes
        }
        if (seq3.isEmpty) None else Some(FScape.Update(proc, seq3))
      }
    }

    final protected def writeData(out: DataOutput): Unit = {
      out.writeShort(SER_VERSION)
      graph     .write(out)
      outputsMap.write(out)
    }

    final protected def disposeData()(implicit tx: S#Tx): Unit = {
      disconnect()
      graph     .dispose()
      outputsMap.dispose()
    }

    object outputs extends Outputs[S] {
      protected def fire(added: Option[Output[S]], removed: Option[Output[S]])
                        (implicit tx: S#Tx): Unit = {
        val b = Vector.newBuilder[FScape.OutputsChange[S]]
        b.sizeHint(2)
        // convention: first the removals, then the additions. thus, overwriting a key yields
        // successive removal and addition of the same key.
        removed.foreach { output =>
          b += FScape.OutputRemoved[S](output)
        }
        added.foreach { output =>
          b += FScape.OutputAdded  [S](output)
        }

        proc.changed.fire(FScape.Update(proc, b.result()))
      }

      private def add(key: String, value: Output[S])(implicit tx: S#Tx): Unit = {
        val optRemoved = outputsMap.put(key, value)
        fire(added = Some(value), removed = optRemoved)
      }

      def remove(key: String)(implicit tx: S#Tx): Boolean =
        outputsMap.remove(key).exists { output =>
          fire(added = None, removed = Some(output))
          true
        }

      def add(key: String, tpe: Obj.Type)(implicit tx: S#Tx): Output[S] =
        get(key).getOrElse {
          val res = OutputImpl[S](proc, key, tpe)
          add(key, res)
          res
        }

      def get(key: String)(implicit tx: S#Tx): Option[Output[S]] = outputsMap.get(key)

      def keys(implicit tx: S#Tx): Set[String] = outputsMap.keysIterator.toSet

      def iterator(implicit tx: S#Tx): Iterator[Output[S]] = outputsMap.iterator.map(_._2)
    }
  }

  private final class New[S <: Sys[S]](implicit tx0: S#Tx) extends Impl[S] {
    protected val targets: Targets[S] = Targets(tx0)
    val graph     : GraphObj.Var[S]                     = GraphObj.newVar(GraphObj.empty)
    val outputsMap: SkipList.Map[S, String, Output[S]]  = SkipList.Map.empty
    connect()(tx0)
  }

  private final class ReadOLD[S <: Sys[S]](in: DataInput, access: S#Acc, protected val targets: Targets[S])
                                          (implicit tx0: S#Tx)
    extends ImplOLD[S] {

    val graph: GraphObj.Var[S] = GraphObj.readVar(in, access)
  }

  private final class Read[S <: Sys[S]](in: DataInput, access: S#Acc, protected val targets: Targets[S])
                                       (implicit tx0: S#Tx)
    extends Impl[S] {

    val graph     : GraphObj.Var[S]                     = GraphObj    .readVar(in, access)
    val outputsMap: SkipList.Map[S, String, Output[S]]  = SkipList.Map.read   (in, access)
  }
}