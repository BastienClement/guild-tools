package gt.component.widget

import gt.component.GtHandler
import model.Toon
import util.jsannotation.js
import xuen.Component

object BnetThumb extends Component[BnetThumb](
	selector = "bnet-thumb",
	templateUrl = "/assets/imports/bnet.html"
)

@js class BnetThumb extends GtHandler {
	val toon = property[Toon]
	val src = toon ~ { toon =>
		Option(toon) match {
			case Some(t) =>
				val alt = s"wow/static/images/2d/avatar/${toon.race}-${toon.gender}.jpg"
				if (t.thumbnail != null && t.thumbnail.endsWith(".jpg")) {
					s"https://render-api-eu.worldofwarcraft.com/static-render/eu/${toon.thumbnail}?alt=$alt"
				} else {
					s"https://eu.battle.net/$alt"
				}

			case None =>
				"https://eu.battle.net/wow/static/images/2d/avatar/0-0.jpg"
		}
	}
}

