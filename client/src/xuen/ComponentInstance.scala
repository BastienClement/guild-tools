package xuen

import facade.ShadowDOM._
import org.scalajs.dom.raw.HTMLElement
import scala.scalajs.js.annotation.ScalaJSDefined
import util.implicits._

/**
  * An instance of a component
  */
@ScalaJSDefined
abstract class ComponentInstance extends HTMLElement {
	/** The component object defining this component */
	@inline final def component: Component[this.type] = this.dyn.__component__.as[Component[this.type]]

	/** The shadow root of this element */
	@inline final def shadow: ShadowRoot = this.shadowRoot
}
