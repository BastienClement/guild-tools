package gt.component.widget.form

import scala.scalajs.js
import util.jsannotation.js

/**
  * Created by galedric on 07.08.2016.
  */
@js trait Interactive extends js.Any {
	def click(): Unit
	def mouseenter(): Unit
	def mouseleave(): Unit
}
