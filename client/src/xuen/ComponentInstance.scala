package xuen

import facades.dom4
import facades.dom4.ShadowRoot
import scala.scalajs.js.annotation.ScalaJSDefined
import utils.implicits._

/**
  * An instance of a component
  */
@ScalaJSDefined
abstract class ComponentInstance extends dom4.HTMLElement {
	/** The component object defining this component */
	@inline final def component: Component[this.type] = this.dyn.__component__.as[Component[this.type]]

	/** The shadow root of this element */
	@inline final def shadow: ShadowRoot = this.shadowRoot
}
