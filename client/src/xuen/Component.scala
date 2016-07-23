package xuen

import facade.HTMLTemplateElement
import gt.Loader
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.higherKinds
import scala.scalajs.js
import scala.scalajs.js.ConstructorTag
import util.Global._
import util.implicits._

abstract class Component[H <: ComponentInstance : ConstructorTag](val selector: String,
                                                                  val templateUrl: String = null,
                                                                  val dependencies: Seq[_ <: Component[_]] = Seq()) {
	/** Component template */
	private[xuen] var template: Option[Template] = None

	/** This component constructor tag object */
	private[xuen] val constructorTag = implicitly[ConstructorTag[H]]

	/** Loads the component */
	def load(): Future[_] = loadOnce

	private[this] lazy val loadOnce: Future[_] = {
		for {
			_ <- loadDependencies()
			tmpl <- loadTemplate()
		} yield {
			template = tmpl
			dynamic.document.registerElement(selector, literal(prototype = prototype))
		}
	}

	/** Loads the component dependencies */
	private[this] def loadDependencies(): Future[Seq[_]] = Future.sequence(dependencies.map(d => d.load()))

	/** Load the component template */
	private[this] def loadTemplate(): Future[Option[Template]] = {
		Option(templateUrl).map { url =>
			Loader.loadDocument(url).map(doc => doc.querySelector(s"xuen-component[id='$selector']")).collect {
				case null => throw XuenException(s"No <xuen-component> found for '$selector' in '$url'")
				case element => element.querySelector(s"xuen-component[id='$selector'] > template")
			}.collect {
				case null => throw XuenException(s"No <template> found for component '$selector' in '$url'")
				case tmpl => Some(Template(tmpl.asInstanceOf[HTMLTemplateElement]))
			}
		}.getOrElse {
			Future.successful(None)
		}
	}

	/** Constructs the component syntethic prototype */
	private[this] final lazy val prototype: js.Object = {
		// The base inheritance for custom elements
		val inheritFrom = js.Dynamic.global.HTMLElement.prototype

		// Flatten the original prototype chain into a single node under the HTMLElement prototype
		def flatten(target: js.Object, source: js.Object): Unit = {
			val proto = js.Object.getPrototypeOf(source)
			if (proto.? && (proto.dyn !== inheritFrom)) flatten(target, proto)
			for (name <- js.Object.getOwnPropertyNames(source) if name != "constructor") {
				js.Object.defineProperty(target, name, js.Object.getOwnPropertyDescriptor(source, name))
			}
		}

		val constructor = constructorTag.constructor
		val prototype = js.Object.create(inheritFrom.as[js.Object]).dyn

		flatten(prototype.as[js.Object], constructor.dyn.prototype.as[js.Object])
		prototype.__component__ = this.dyn

		prototype.as[js.Object]
	}
}
