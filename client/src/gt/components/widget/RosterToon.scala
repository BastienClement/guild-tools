package gt.components.widget

import gt.components.GtHandler
import gt.services.RosterService
import rx.Obs
import utils.jsannotation.js
import xuen.Component

object RosterToon extends Component[RosterToon](
	selector = "roster-toon"
)

@js class RosterToon extends GtHandler {
	val roster = service(RosterService)

	val user = attribute[Int]
	val toon = attribute[Int]

	val effectiveToon = user ~! {
		case 0 => roster.toon(toon)
		case i if i > 0 => roster.main(i)
	}

	private val toonObs = Obs {
		val toon = effectiveToon.!
		textContent = toon.name
		setAttribute("class-color", toon.classid.toString)
	}

	override def attached(): Unit = effectiveToon ~>> toonObs
	override def detached(): Unit = effectiveToon ~/> toonObs
}
