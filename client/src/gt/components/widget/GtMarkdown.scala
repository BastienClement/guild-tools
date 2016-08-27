package gt.components.widget

import gt.components.GtHandler
import utils.jsannotation.js
import xuen.Component

object GtMarkdown extends Component[GtMarkdown](
	selector = "gt-markdown",
	templateUrl = "/assets/imports/markdown.html"
)

@js class GtMarkdown extends GtHandler {
	val src = property[String]
}
