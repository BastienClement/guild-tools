package facades.dom4

import org.scalajs.dom
import scala.scalajs.js

@js.native
trait ShadowRoot extends dom.DocumentFragment {
	val host: Element
}
