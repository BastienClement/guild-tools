package util

import org.scalajs
import org.scalajs.dom._
import scala.concurrent.{Future, Promise}
import scala.language.implicitConversions
import scala.scalajs.js

object implicits {
	implicit class ElementLoadFuture[E <: scalajs.dom.Element](val element: E) extends AnyVal {
		@inline def onLoadFuture: Future[E] = {
			val promise = Promise[E]()
			element.addEventListener("load", (_: Any) => promise.success(element))
			promise.future
		}
	}

	implicit class NodeListSeq[T <: Node](nodes: DOMList[T]) extends IndexedSeq[T] {
		override def length: Int = nodes.length
		override def apply(idx: Int): T = nodes(idx)
		override def foreach[U](f: T => U): Unit = {
			var i = 0
			while (i < nodes.length) {
				f(nodes(i))
				i += 1
			}
		}
	}

	implicit class NamedNodeMapSeq(nodes: NamedNodeMap) extends IndexedSeq[Node] {
		override def length: Int = nodes.length
		override def apply(idx: Int): Node = nodes(idx)
		override def foreach[U](f: Node => U): Unit = {
			var i = 0
			while (i < nodes.length) {
				f(nodes(i))
				i += 1
			}
		}
	}

	implicit class ImplicitJsTruthy[T](private val value: T) extends AnyVal {
		@inline def ? : Boolean = {
			(value != null) && (value.asInstanceOf[js.UndefOr[T]] ne js.undefined)
		}
	}

	implicit class ToDynamic(private val value: Any) extends AnyVal {
		@inline def dyn: js.Dynamic = value.asInstanceOf[js.Dynamic]
	}

	implicit class FromAny(private val dyn: js.Any) extends AnyVal {
		@inline def as[T]: T = dyn.asInstanceOf[T]
	}

	implicit class FromDynamic(private val dyn: js.Dynamic) extends AnyVal {
		@inline def as[T]: T = dyn.asInstanceOf[T]
	}

	implicit class EqEqEq[T <: AnyRef](private val lhs: T) extends AnyVal {
		@inline def === (rhs: T): Boolean = lhs eq rhs
		@inline def !== (rhs: T): Boolean = lhs ne rhs
	}

	implicit class ExponentOperator(private val lhs: Double) extends AnyVal {
		@inline def ** (rhs: Double): Double = {
			Math.pow(lhs, rhs)
		}
	}
}
