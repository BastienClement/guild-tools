package facades.dom4

import org.scalajs.dom
import scala.scalajs.js

@js.native
trait Element extends dom.Element with ChildNode {
	/**
	  * Returns the youngest shadow root that is hosted by the element
	  */
	val shadowRoot: ShadowRoot = js.native

	/**
	  * Returns the Element, descendant of this element (or this element itself), that
	  * is the closest ancestor of the elements selected by the selectors given in parameter.
	  */
	def closest(selectors: String): Element = js.native

	/**
	  * Creates a shadow DOM on on the element, turning it into a shadow host
	  */
	def createShadowRoot(): ShadowRoot = js.native
}
