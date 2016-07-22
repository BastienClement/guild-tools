package xuen.expr

import org.scalajs.dom.console
import scala.collection.mutable
import scala.scalajs.js

/** Dummy marker trait */
trait PipesCollection {
	def declare(name: String, fn: js.Function): Unit = PipesRegistry.register(name, fn)
}

object PipesRegistry {
	private[this] val pipes = mutable.Map.empty[String, js.Function]

	def register(name: String, fn: js.Function) = pipes.put(name, fn)

	def invoke(name: String, value: Any, args: Seq[Any]): Any = pipes.get(name) match {
		case Some(fn) =>
			fn.call(null, (value +: args).asInstanceOf[Seq[js.Any]]: _*)

		case None =>
			console.warn(s"Usage of undefined pipe '$name' ignored.")
			value
	}

	def load(col: PipesCollection): Unit = {
		// Does nothing, the fact that it receive an instance of PipesCollection
		// is enough to make the collection be loaded
	}
}
