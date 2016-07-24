package xuen

import org.scalajs.dom.raw.HTMLLinkElement
import org.scalajs.dom.{console => _, document => _, _}
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSStringOps.enableJSStringOps
import scala.scalajs.js.RegExp
import util.Global._
import util.GtWorker
import util.implicits._

object Loader {
	private val fetchCache = mutable.Map.empty[String, Future[String]]
	private val lessCache = mutable.Map.empty[String, Future[_]]
	private val documentCache = mutable.Map.empty[String, Future[Document]]

	/** Wrapper around the JS fetch() function */
	private def rawFetch(url: String): Future[String] = {
		val response = dynamic.fetch(url)
		response.`then`((response: js.Dynamic) => response.text()).as[js.Promise[String]].toFuture
	}

	/** Fetches an url and returns its text representation */
	def fetch(url: String, cache: Boolean = true): Future[String] = {
		def data = rawFetch(url)
		if (cache) fetchCache.getOrElseUpdate(url, data)
		else data
	}

	/** Loads a LESS file and add it to the document head */
	def loadLess(url: String): Future[_] = {
		lessCache.getOrElseUpdate(url, {
			fetch(url).flatMap(LessLoader.compile).map { css =>
				val style = document.createElement("style")
				style.innerHTML = css
				style.setAttribute("data-source", url)
				document.head.appendChild(style)
			}
		})
	}

	/** Performs an HTML import */
	def loadDocument(url: String): Future[Document] = {
		documentCache.getOrElseUpdate(url, {
			val link = document.createElement("link").as[HTMLLinkElement]
			link.rel = "import"
			link.setAttribute("async", "")
			link.href = url
			document.head.appendChild(link)
			link.onLoadFuture.map { e =>
				e.asInstanceOf[js.Dynamic].`import`.as[Document]
			}
		})
	}

	/*
	/** Loads a polymer element */
	def loadElement[C <: XuenComponent[_]](element: C): Future[_] = {
		elementCache.getOrElseUpdate(element, {
			for {
				_ <- loadPolymer
				_ <- Future.sequence(element.dependencies.map(loadElement))
				_ <- element.templateURL.map(PolymerLoader.loadTemplate(_)(element)).getOrElse(Future.successful(()))
			} yield {
				// Polymer is doing something stupid with document._currentScript, fake it.
				val document = dynamic.document
				document._currentScript = document.body

				// Register
				Polymer(element.handler)

				document.asInstanceOf[js.Dictionary[_]].delete("_currentScript")
				loadedElements.add(element.selector)
			}
		})
	}

	/** Checks whether an element was already loaded */
	def elementIsLoaded(selector: String): Boolean = loadedElements.contains(selector)

	/** Polymer loader */
	private object PolymerLoader {
		val dummy = document.createElement("div")

		/** Loads a polymer template */
		def loadTemplate(url: String)(implicit element: XuenComponent[_]): Future[_] = {
			for {
				doc <- loadDocument(url)
				module = Option(doc.querySelector(s"dom-module[id=${element.selector}]")).getOrElse {
					throw new Exception(s"No <dom-module> found for element <${element.selector}> in file '$url'")
				}
				_ <- Future.sequence(Seq(
					importExternalLess(module),
					compileLess(module),
					compileTemplate(module)
				))
			} yield {}
		}

		/** Imports external less sources */
		def importExternalLess(module: Element)(implicit element: XuenComponent[_]): Future[_] = {
			val externals = module.querySelectorAll("link[rel=\"stylesheet/less\"]").as[NodeListOf[HTMLLinkElement]]
			val jobs = externals.map { link =>
				fetch(link.href).map(extractNamespace(link.getAttribute("ns"))).flatMap(LessLoader.compile).map { css =>
					// Create the style element
					val style = document.createElement("style").as[HTMLLinkElement]
					style.innerHTML = css

					// Replace the <link> tag by an inline <style>
					link.parentNode.replaceChild(style, link)
				}
			}
			Future.sequence(jobs)
		}

		/** Extracts namespace from a compound stylesheet */
		def extractNamespace(ns: String)(src: String)(implicit element: XuenComponent[_]) = {
			if (ns.isEmpty) src
			else {
				val ns_key = s""":namespace("$ns")"""
				val ns_start = src.indexOf(ns_key)
				if (ns_start < 0) throw new Exception(s"No $ns_key found while loading <${element.selector}>")

				var level = 0
				var block_start, block_end = 0
				var o = ns_start + ns_key.length
				var break = false

				while (!break && o < src.length) {
					src.charAt(o) match {
						case '{' =>
							if (level == 0) {
								block_start = o
							}
							level += 1
							o += 1

						case '}' =>
							if (level == 1) {
								block_end = o
								break = true
							} else {
								level -= 1
								o += 1
							}

						case c if !c.isWhitespace && level == 0 =>
							throw new Exception(s"Unexpected char '$c' before namespace block opening")

						case _ =>
							o += 1
					}
				}

				src.substring(block_start + 1, block_end)
			}
		}

		/** Compiles inline LESS styles */
		def compileLess(module: Element): Future[_] = {
			val styles = module.querySelectorAll("style[type=\"text/less\"]").as[NodeListOf[HTMLStyleElement]]
			val jobs = styles.map { style =>
				LessLoader.compile(style.innerHTML).map { css =>
					val new_style = document.createElement("style").as[HTMLStyleElement]
					new_style.innerHTML = css

					style.parentNode.insertBefore(new_style, style)
					style.parentNode.removeChild(style)
				}
			}
			Future.sequence(jobs)
		}

		/** Compiles Polymer template */
		def compileTemplate(module: Element)(implicit element: XuenComponent[_]): Future[_] = Future {
			for (template <- Option(module.getElementsByTagName("template")(0).dyn)) {
				checkUnloadedElements(template.content.as[HTMLElement])
				compilePolymerSugars(template.content.as[DocumentFragment])
				compileAngularNotation(template.content.as[HTMLElement])
			}
		}

		/** Walks the DOM tree a look for unloaded custom elements */
		def checkUnloadedElements(root: Element)(implicit element: XuenComponent[_]): Unit = {
			if (root.nodeType != 11 && root.tagName.contains("-") && !elementIsLoaded(root.tagName.toLowerCase)) {
				throw new Exception(s"Detected unloaded element <${root.tagName.toLowerCase}> in template for <${element.selector}>")
			}

			if (root.dyn.getAttribute.?) {
				val is = root.getAttribute("is")
				if (is != null && !elementIsLoaded(is) && !elementIsLoaded(is + "-provider")) {
					throw new Exception(s"Detected unloaded element <${ root.tagName.toLowerCase } is=$is> in template for <${element.selector}>")
				}
			}

			root.children.foreach(checkUnloadedElements)
		}

		/** Compiles Polymer template sugars */
		def compilePolymerSugars(template: DocumentFragment): Unit = {
			var node: HTMLElement = null
			var wrapper = document.createElement("template")

			// Attribute promotion helper
			@tailrec
			def promote_attribute(from: String, to: String = null, default: String = null, braces: Boolean = false): Unit = {
				if (to == null) {
					promote_attribute(from, from, default, braces)
				} else if (node.hasAttribute(s"($from)")) {
					promote_attribute(s"($from)", to, default, braces)
				} else {
					val value = Option(node.getAttribute(from)).getOrElse(default)
					if (value != null) {
						var binding = compileBindingsSugar(value)
						node.removeAttribute(from)

						if (braces && !binding.matches("""\{\{.*\}\}""")) {
							binding = s"{{$binding}}"
						}

						val target = if (node.tagName == "TEMPLATE") node else wrapper
						target.setAttribute(to, binding)
					}
				}
			}

			// Move node inside the wrapper
			def promote_node(behaviour: String): Unit = {
				if (node.tagName != "TEMPLATE") {
					node.parentNode.insertBefore(wrapper, node)
					wrapper.setAttribute("is", behaviour)
					wrapper.dyn.content.appendChild(node)
					wrapper = document.createElement("template")
				} else {
					node.setAttribute("is", behaviour)
				}
			}

			// Note: we need to find all interesting nodes before promoting any one of them.
			// If we promote *if nodes before looking for *for ones, it is possible for
			// some of them to get nested inside the wrapper.content shadow tree when we
			// attempt to querySelectorAll and they will not be returned.

			// <element *if="cond">
			val if_nodes = template.querySelectorAll("*[\\*if]").as[NodeListOf[HTMLElement]]

			// <element *for="collection" filter sort observe>
			val repeat_nodes = template.querySelectorAll("*[\\*for]").as[NodeListOf[HTMLElement]]

			for (if_node <- if_nodes) {
				node = if_node
				promote_attribute("*if", "if", node.textContent, true)
				promote_node("dom-if")
			}

			for (repeat_node <- repeat_nodes) {
				node = repeat_node
				promote_attribute("*for", "item", "", true)
				promote_attribute("*filter", "filter")
				promote_attribute("*sort", "sort")
				promote_attribute("*observe", "observe")
				promote_attribute("*id", "id")
				promote_attribute("*as", "as")
				promote_attribute("*index-as", "index-as")
				promote_node("dom-repeat")
			}

			// Transform <meta is="..."> to <meta is="...-provider">
			val meta_is_nodes = template.querySelectorAll("meta[is]").as[NodeListOf[HTMLMetaElement]]
			for (meta <- meta_is_nodes) {
				meta.setAttribute("is", meta.getAttribute("is") + "-provider")
			}

			// Recurse on children
			val children = template.querySelectorAll("template")
			for (child <- children) compilePolymerSugars(child.dyn.content.as[DocumentFragment])
		}

		/** Compiles binding sugar */
		def compileBindingsSugar(binding: String): String = {
			val matches = binding.`match`(RegExp("""^([^\s]+)\s*([<=>]=?|!=)\s*([^\s]+)$"""))
			if (matches.?) {
				matches(2) match {
					case "=" | "==" => s"eq(${ matches(1) }, ${ matches(3) })"
					case "!=" => s"neq(${ matches(1) }, ${ matches(3) })"
					case "<" => s"lt(${ matches(1) }, ${ matches(3) })"
					case "<=" => s"lte(${ matches(1) }, ${ matches(3) })"
					case ">" => s"gt(${ matches(1) }, ${ matches(3) })"
					case ">=" => s"gte(${ matches(1) }, ${ matches(3) })"
				}
			} else {
				binding
			}
		}

		/** Compiles Angular notation shortcuts */
		def compileAngularNotation(node: HTMLElement): Unit = if (node.?) {
			val attrs: Seq[(String, String, String)] = if (node.attributes.?) {
				for (i <- 0 until node.attributes.length) yield {
					val attr = node.attributes(i)
					(attr.name, attr.value, attr.name.jsSlice(1, -1))
				}
			} else Nil

			val children = node.childNodes
			var attr_bindings_compiled = false

			for ((name, value, bind) <- attrs) {
				name.charAt(0) match {
					case '[' =>
						node.removeAttribute(name)
						node.setAttribute(bind, "{{" + (compileBindingsSugar(value).dyn || bind.dyn) + "}}")
					case '(' =>
						node.removeAttribute(name)
						node.setAttribute(s"on-$bind", value)
					case '{' =>
						// Dumb browsers preventing special chars in attribute names
						if (!attr_bindings_compiled) {
							compileAttributeBindings(node, attrs)
							attr_bindings_compiled = true
						}
					case '#' =>
						node.removeAttribute(name)
						node.setAttribute("id", name.substring(1))
					case '.' =>
						node.removeAttribute(name)
						node.classList.add(name.substring(1))
					case _ => // ignore
				}
			}

			// Recurse on children
			for (child <- children) {
				compileAngularNotation((child.dyn.content || child.dyn).as[HTMLElement])
			}
		}

		/** Compiles attribute bidings */
		def compileAttributeBindings(node: HTMLElement, attrs: Seq[(String, String, String)]): Unit = {
			// Removes attributes that are not {} bindings
			val bindings_attrs = attrs.filter { case (name, _, _) => name.startsWith("{") }

			// Construct the new tag
			// -> Special case for empty tag
			var tag = node.outerHTML.slice(0, node.outerHTML.indexOf(">") + 1)
			for ((name, _, bind) <- bindings_attrs) {
				tag = tag.replace(name, bind + "$")
			}

			// Replace the element name by <div>
			// Without this, an instance of the element is incorrectly created
			tag = tag.jsReplace(RegExp("""^<[^\s]+"""), "<div")

			// Instantiate
			dummy.innerHTML = tag
			val new_node = dummy.firstChild.as[HTMLElement]

			// Copy attributes
			for ((name, value, bind) <- bindings_attrs) {
				val attr_node = new_node.attributes.getNamedItem(bind + "$").cloneNode(false).as[Attr]
				attr_node.value = "{{" + (compileBindingsSugar(value).dyn || bind.dyn) + "}}"
				node.attributes.setNamedItem(attr_node)
				node.removeAttribute(name)
			}

			// Cleanup
			dummy.innerHTML = ""
		}
	}
*/

	/** LESS loader */
	private object LessLoader {
		/** The shared library path */
		final val SHARED_LIB = "/assets/less/lib.less"

		/** The LESS compiler worker */
		val worker = GtWorker.singleton("/assets/workers/less.js")

		/** Compiles a source of LESS code to CSS */
		def compile(source: String): Future[String] = {
			importDynamics(s"""@import (dynamic) "$SHARED_LIB";\n$source""").flatMap(workerCompile).map(styleFix)
		}

		/** Handles @import (dynamic) instructions */
		def importDynamics(source: String): Future[String] = {
			val parts = source.jsSplit(RegExp("""@import\s*\(dynamic\)\s*"([^"]*)";?"""))
			if (parts.length == 1) {
				Future.successful(source)
			} else {
				val tasks = parts.toVector.zipWithIndex.map {
					case (part, idx) if idx % 2 == 1 => Loader.fetch(part)
					case (part, _) => Future.successful(part)
				}
				Future.sequence(tasks).map(res => res.mkString).flatMap(importDynamics)
			}
		}

		/** Performs worker compilation */
		def workerCompile(source: String): Future[String] = {
			worker.request("compile", source)
		}

		/** Performs style fixes with PrefixFree */
		def styleFix(source: String): String = {
			dynamic.StyleFix.fix(source, true).as[String]
		}
	}
}
