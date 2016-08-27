package facades.dom4

import org.scalajs.dom.DocumentFragment
import scala.scalajs.js

/**
  * The template element is used to declare fragments of HTML
  * that can be cloned and inserted in the document by script.
  */
@js.native
class HTMLTemplateElement extends HTMLElement {
	/**
	  * The content IDL attribute must return the template
	  * element's template contents.
	  */
	val content: DocumentFragment = js.native
}
