package xuen

import facades.dom4.HTMLTemplateElement
import gt.App
import org.scalajs.dom.raw.{HTMLLinkElement, HTMLStyleElement}
import org.scalajs.dom.{console, _}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.higherKinds
import scala.scalajs.js
import scala.scalajs.js.ConstructorTag
import scala.util.Failure
import utils.Global._
import utils.implicits._
import xuen.Loader.LessLoader

abstract class Component[H <: ComponentInstance : ConstructorTag](val selector: String,
                                                                  val templateUrl: String = null,
                                                                  val dependencies: Seq[_ <: Component[_]] = Seq()) {
	require(selector.toLowerCase == selector)

	/** Component template */
	private[xuen] var template: Option[Template] = None

	/** This component constructor tag object */
	private[xuen] val constructorTag = implicitly[ConstructorTag[H]]

	/** Loads the component */
	def load(): Future[_] = loadOnce

	private[this] lazy val loadOnce: Future[_] = {
		for {
			_ <- loadDependencies()
			tmpl <- loadTemplate() andThen {
				case Failure(e) => console.error("Failed to load template: " + App.formatException(e))
			}
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
			Loader.loadDocument(url).map(doc => doc.querySelector(s"xuen-component[id='$selector']")).flatMap {
				case null => throw XuenException(s"No <xuen-component> found for '$selector' in '$url'")
				case element =>
					@inline def importExternalLess: Future[_] = {
						val externals = element.querySelectorAll(s"xuen-component[id='$selector'] > link[rel='stylesheet/less']").as[NodeListOf[HTMLLinkElement]]
						val jobs = externals.map { link =>
							Loader.fetch(link.href).flatMap(LessLoader.compile(_, link.getAttribute("ns"))).map { css =>
								// Create the style element
								val style = element.ownerDocument.createElement("style").as[HTMLLinkElement]
								style.`type` = "text/css"
								style.innerHTML = css

								// Replace the <link> tag by an inline <style>
								link.parentNode.replaceChild(style, link)
							}
						}
						Future.sequence(jobs)
					}

					@inline def compileLess: Future[_] = {
						val styles = element.querySelectorAll(s"xuen-component[id='$selector'] > style[type='text/less']").as[NodeListOf[HTMLStyleElement]]
						val jobs = styles.map { style =>
							LessLoader.compile(style.innerHTML).map { css =>
								val new_style = element.ownerDocument.createElement("style").as[HTMLStyleElement]
								new_style.innerHTML = css

								style.parentNode.insertBefore(new_style, style)
								style.parentNode.removeChild(style)
							}
						}
						Future.sequence(jobs)
					}

					Future.sequence(Seq(importExternalLess, compileLess)).map { _ =>
						val tmpl = element.querySelector(s"xuen-component[id='$selector'] > template").asInstanceOf[HTMLTemplateElement]
						val styles = element.querySelectorAll(s"xuen-component[id='$selector'] > style").as[NodeListOf[HTMLStyleElement]]
						(tmpl, styles)
					}
			}.collect {
				case null => throw XuenException(s"No <template> found for component '$selector' in '$url'")
				case (tmpl, styles) =>
					for (style <- styles) tmpl.content.appendChild(style)
					val template = new Template(tmpl, this)
					for (dep <- dependencies if !template.componentChilds.contains(dep.selector)) {
						console.warn(s"Ununsed dependency declaration: <$selector> => <${ dep.selector }>")
					}
					Some(template)
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
