package facade.dom4

import org.scalajs.dom
import scala.language.implicitConversions

object implicits {
	@inline implicit def UpgradeElement(element: dom.Element): Element = element.asInstanceOf[Element]
}
