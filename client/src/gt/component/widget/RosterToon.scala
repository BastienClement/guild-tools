package gt.component.widget

import gt.component.GtHandler
import gt.service.RosterService
import util.jsannotation.js
import xuen.Component

object RosterToon extends Component[RosterToon](
	selector = "roster-toon"
)

@js class RosterToon extends GtHandler {
	val roster = service(RosterService)

	val user = attribute[Int]
	val toon = attribute[Int]

	val effectiveToon = (user ~! roster.main) ~+ (toon ~! roster.toon)

	val `class-color` = attribute[Int] <~ (effectiveToon ~ (_.clss))
	effectiveToon ~> (t => textContent = t.name)
}
