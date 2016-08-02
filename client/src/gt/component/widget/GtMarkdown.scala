package gt.component.widget

import gt.component.GtHandler
import util.jsannotation.js
import xuen.Component

object GtMarkdown extends Component[GtMarkdown](
	selector = "gt-markdown",
	templateUrl = "/assets/imports/markdown.html"
)

@js class GtMarkdown extends GtHandler {
	val src = property[String]
}
