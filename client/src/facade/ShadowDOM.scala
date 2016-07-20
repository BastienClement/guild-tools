package facade

import org.scalajs.dom.{DocumentFragment, Element}
import scala.language.implicitConversions
import scala.scalajs.js

object ShadowDOM {
	@js.native
	trait ElementOps extends Element {
		def createShadowRoot(): ShadowRoot
		val shadowRoot: ShadowRoot
	}

	@js.native
	trait ShadowRoot extends DocumentFragment {
		val host: Element
	}

	implicit def elementOps(e: Element): ElementOps = e.asInstanceOf[ElementOps]
}
