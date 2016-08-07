package facade.dom4

import scala.scalajs.js

@js.native
trait ChildNode extends js.Any {
	/**
	  * Removes this ChildNode from the children list of its parent.
	  */
	def remove(): Unit = js.native
}
