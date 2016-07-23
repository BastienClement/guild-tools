package xuen

import scala.language.dynamics
import scala.scalajs.js
import scala.scalajs.js.UndefOr
import util.implicits._

/**
  * A Context used during expression evaluation.
  *
  * The context is responsible for providing value for specific keys that can
  * be used in a Xuen expression.
  */
sealed trait Context extends Dynamic {
	def has(name: String): Boolean
	def get(name: String): Any
	def set(name: String, value: Any): Unit

	def invoke(name: String, args: Seq[Any]): Any
	def invokeTarget: js.Dynamic

	def selectElement(selector: String): Any

	def selectDynamic(name: String): Any
	def updateDynamic(name: String)(value: Any): Unit
	def applyDynamic(name: String)(args: Any*): Any

	def child(properties: (String, Any)*): Context.Child
}

object Context {
	private[Context] def performInvoke(target: js.Dynamic, function: Any, args: Seq[Any]): Any = function match {
		case fun: js.Function => fun.call(target, args.asInstanceOf[Seq[js.Any]]: _*)
		case _ => throw XuenException("Invoke target is not a function")
	}

	class Reference(val ref: js.Dynamic) extends Context {
		def has(name: String): Boolean = get(name).asInstanceOf[UndefOr[Any]].isDefined
		def get(name: String): Any = ref.dyn.selectDynamic(name)
		def set(name: String, value: Any): Unit = ref.selectDynamic(name) match {
			case fn: js.Function => throw XuenException("Overriding a function property on the reference object is not allowed")
			case any if any.asInstanceOf[UndefOr[Any]].isDefined => ref.updateDynamic(name)(value.asInstanceOf[js.Any])
			case _ => throw XuenException("Setting an undefined property on the reference object is not allowed")
		}

		def invokeTarget: js.Dynamic = ref
		def invoke(name: String, args: Seq[Any]): Any = performInvoke(invokeTarget, get(name), args)

		def selectElement(selector: String): Any = ref.$xuen$selectElement(selector)

		def selectDynamic(name: String): Any = get(name)
		def updateDynamic(name: String)(value: Any): Unit = set(name, value)
		def applyDynamic(name: String)(args: Any*): Any = invoke(name, args)

		def child(properties: (String, Any)*): Child = Context.child(this, properties: _*)
	}

	class Child(val parent: Context) extends Context {
		private[this] val locals = js.Object.create(null).as[js.Dictionary[Any]]

		def has(name: String): Boolean = locals.contains(name) || parent.has(name)
		def get(name: String): Any = locals.getOrElse(name, parent.get(name))
		def set(name: String, value: Any): Unit = locals.put(name, value)

		def invokeTarget: js.Dynamic = parent.invokeTarget
		def invoke(name: String, args: Seq[Any]): Any = performInvoke(invokeTarget, get(name), args)

		def selectElement(selector: String): Any = parent.selectElement(selector)

		def selectDynamic(name: String): Any = get(name)
		def updateDynamic(name: String)(value: Any): Unit = set(name, value)
		def applyDynamic(name: String)(args: Any*): Any = invoke(name, args)

		def child(properties: (String, Any)*): Child = Context.child(this, properties: _*)

		override def toString: String = {
			super.toString + "(" + locals.map { case (k, v) => s"$k=$v" }.mkString(", ") + ")"
		}
	}

	def ref(r: Any): Reference = new Reference(r.asInstanceOf[js.Dynamic])

	def child(parent: Context, properties: (String, Any)*): Child = {
		val child = new Child(parent)
		for ((name, value) <- properties) child.set(name, value)
		child
	}
}
