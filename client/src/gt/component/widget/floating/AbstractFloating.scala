package gt.component.widget.floating

import facade.dom4.HTMLElement
import rx.Var
import util.jsannotation.js

@js trait AbstractFloating extends HTMLElement {
	val visible: Var[Boolean]
	var transition: Boolean
	var placeholder: GtFloatingPlaceholder
}
