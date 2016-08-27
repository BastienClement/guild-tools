package xuen

import org.scalajs.dom.raw.HTMLLinkElement
import org.scalajs.dom.{console => _, document => _, _}
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSStringOps.enableJSStringOps
import scala.scalajs.js.RegExp
import utils.Global._
import utils.GtWorker
import utils.implicits._
import xuen.compat.Platform

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
			fetch(url).flatMap(LessLoader.compile(_)).map { css =>
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
				if (Platform.isFirefox) {
					e.asInstanceOf[js.Dynamic].__doc.as[Document]
				} else {
					e.asInstanceOf[js.Dynamic].`import`.as[Document]
				}
			}
		})
	}

	/** LESS loader */
	object LessLoader {
		/** The shared library path */
		final val SHARED_LIB = "/assets/less/lib.less"

		/** The LESS compiler worker */
		val worker = GtWorker.singleton("/assets/workers/less.js")

		/** Compiles a source of LESS code to CSS */
		def compile(source: String, ns: String = null): Future[String] = {
			val effectiveSource = extractNamespace(ns, source)
			importDynamics(s"""@import (dynamic) "$SHARED_LIB";\n$effectiveSource""").flatMap(workerCompile).map(styleFix)
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

		/** Extracts namespace from a compound stylesheet */
		def extractNamespace(ns: String, src: String) = {
			if (ns == null || ns.isEmpty) src
			else {
				val ns_key = s""":namespace("$ns")"""
				val ns_start = src.indexOf(ns_key)
				if (ns_start < 0) throw new Exception(s"No $ns_key found")

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
	}
}
