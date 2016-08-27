package gt.components.widget

import gt.components.GtHandler
import model.Toon
import utils.jsannotation.js
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
				val alt = s"/forums/static/images/avatars/wow/${toon.race}-${toon.gender}.jpg"
				if (t.thumbnail != null && t.thumbnail.endsWith(".jpg")) {
					s"https://render-api-eu.worldofwarcraft.com/static-render/eu/${toon.thumbnail}?alt=$alt"
				} else {
					s"https://eu.battle.net/$alt"
				}

			case None =>
				"https://eu.battle.net/forums/static/images/avatars/wow/0-0.jpg"
		}
	}
}

