package gt.components.widget.floating

import facades.dom4.HTMLElement
import rx.Var
import utils.jsannotation.js

@js trait AbstractFloating extends HTMLElement {
	val visible: Var[Boolean]
	var transition: Boolean
	var placeholder: GtFloatingPlaceholder
}
