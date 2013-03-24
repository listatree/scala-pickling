import scala.pickling._
import json._

import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._
import scala.collection.mutable.ListBuffer

object Test extends App {
  implicit def genListPickler[T](implicit elemPickler: Pickler[T], pf: PickleFormat): Pickler[List[T]] with Unpickler[List[T]] = new Pickler[List[T]] with Unpickler[List[T]] {
    def pickle(picklee: List[T], builder: PickleBuilder): Unit = {
      builder.hintTag(fastTypeTag[List[_]]).beginEntry(picklee)
      builder.putField("numElems", b => b.hintStaticType().beginEntry(picklee.length))
      for (el <- picklee) builder.putField("elem", b => elemPickler.pickle(el, b))
      builder.endEntry()
    }

    def unpickle(tpe: TypeTag[_], reader: PickleReader): Any = {
      reader.readField("numElems")
      reader.hintTag(fastTypeTag[Int]).hintStaticType().beginEntry()
      val length = reader.readPrimitive().asInstanceOf[Int]
      read.endEntry()

      var listbuf = ListBuffer[Any]()
      for (i <- 1 to length) {
        reader.readField("elem")
        val tag = reader.beginEntry()
        val el = Unpickler.genUnpickler(currentMirror, tag)
        listbuf += el.unpickle(tag, reader)
        el.endEntry()
      }

      listbuf.toList
    }
  }

  val pickle = List(1, 2, 3).pickle
  println(pickle.value)
  println(pickle.unpickle[List[Int]])
}
