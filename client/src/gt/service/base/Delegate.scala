package gt.service.base

import boopickle.DefaultBasic._
import gtp3.PickledPayload
import org.scalajs.dom._
import scala.collection.mutable

trait Delegate extends Service {
	private[this] val handlers = mutable.Map[String, PickledPayload => Unit]()
	protected implicit val selfDelegate: Delegate = this

	final def receiveMessage(msg: String, payload: PickledPayload): Unit = handlers.get(msg) match {
		case Some(handler) => handler(payload)
		case None => console.warn(s"Ignored undefined message: $msg")
	}

	final def message(msg: String) = new {
		def apply[T1: Pickler](handler: T1 => Unit): Unit = handlers.put(msg, pp => handler(pp.as[T1]))
		def apply[T1: Pickler, T2: Pickler](handler: (T1, T2) => Unit): Unit = apply(handler.tupled)
		def apply[T1: Pickler, T2: Pickler, T3: Pickler](handler: (T1, T2, T3) => Unit): Unit = apply(handler.tupled)
		def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler](handler: (T1, T2, T3, T3) => Unit): Unit = apply(handler.tupled)
		def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler, T5: Pickler](handler: (T1, T2, T3, T3, T5) => Unit): Unit = apply(handler.tupled)
	}
}

object Delegate {
	implicit object DefaultDelegate extends Delegate {}
}
