/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2013, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

package scala

import java.lang.annotation.Inherited
import scala.annotation.MacroAnnotation
import scala.language.experimental.macros
import scala.reflect.runtime.{universe => ru}
import scala.reflect.api.Universe
import scala.reflect.macros.AnnotationMacro

package object pickling {

  import scala.reflect.macros.Context
  import ir._

  // TOGGLE DEBUGGING
  var debugEnabled: Boolean = System.getProperty("pickling.debug", "false").toBoolean
  def debug(output: => String) = if (debugEnabled) println(output)

  implicit class PickleOps[T](x: T) {
    def pickle(implicit pickler: Pickler[T], format: PickleFormat): Pickle = {
      pickler.pickle(x)
      //format.write(ir)
    }
  }

  def genPicklerImpl[T: c.WeakTypeTag](c: Context): c.Expr[Pickler[T]] = {
    import c.universe._
    val irs = new IRs[c.universe.type](c.universe)
    import irs._

    val tpe = weakTypeOf[T]
    val pickleFormatTree: Tree = c.inferImplicitValue(typeOf[PickleFormat]) match {
      case EmptyTree => c.abort(c.enclosingPosition, "Couldn't find implicit PickleFormat")
      case tree => tree
    }

    // get instance of PickleFormat
    val pickleFormatCarrier = c.typeCheck(Select(pickleFormatTree, TermName("instantiate")), silent = true)
    val pickleFormat = pickleFormatCarrier.attachments.all.find(_.isInstanceOf[PickleFormat]) match {
      case Some(pf: PickleFormat) => pf
      case _ => c.abort(c.enclosingPosition, s"Couldn't instantiate PickleFormat of type ${pickleFormatTree.tpe}")
    }

    // build IR
    debug("The tpe just before IR creation is: " + tpe)
    val oir = flatten(compose(ObjectIR(tpe, null, List())))
    val holes = oir.fields.map(fir => c.Expr[Pickle](Select(Select(Ident(TermName("obj")), TermName(fir.name)), TermName("pickle"))))
    val pickleLogic = pickleFormat.pickle(irs)(oir, holes)
    debug("Pickler.pickle = " + pickleLogic)

    reify {
      implicit val anon$pickler = new Pickler[T] {
        def pickle(raw: Any): Pickle = {
          val obj = raw.asInstanceOf[T]
          pickleLogic.splice
        }
      }
      anon$pickler
    }
  }
}

package pickling {

  trait Pickler[T] {
    def pickle(obj: Any): Pickle
    //def unpickle(p: Pickle): T
  }

  object Pickler {
    implicit def genPickler[T]: Pickler[T] = macro genPicklerImpl[T]
  }

  trait Pickle {
    val value: Any
  }

  @Inherited
  class pickleable extends MacroAnnotation {
    def transform = macro PickleableMacro.impl
  }

  trait PickleableMacro extends AnnotationMacro {
    def impl = {
      import c.universe._
      import Flag._
      c.annottee match {
        case ClassDef(mods, name, tparams, Template(parents, self, body)) =>
          // TODO: implement dispatchTo, add other stuff you find @pickleable should do
          val dispatchTo = DefDef(Modifiers(OVERRIDE), TermName("dispatchTo"), Nil, Nil, TypeTree(), Ident(TermName("$qmark$qmark$qmark")))
          ClassDef(mods, name, tparams, Template(parents, self, body :+ dispatchTo))
      }
    }
  }

  trait HasPicklerDispatch {
    def dispatchTo: Pickler[_]
  }

  trait PickleFormat {
    import ir._
    def instantiate = macro ???
    def pickle[U <: Universe with Singleton](irs: IRs[U])(ir: irs.ObjectIR, holes: List[irs.uni.Expr[Pickle]]): irs.uni.Expr[Pickle]
    // def unpickle[U <: Universe with Singleton](u: U)(pickle: u.Expr[Pickle]): u.Expr[(???.Type, Any)]
  }
}


