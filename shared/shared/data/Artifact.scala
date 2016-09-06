package data

import utils.annotation.data
import _root_.data.Relic._

@data
sealed abstract class Artifact(id: Int, name: String, relics: (Relic, Relic, Relic))

object Artifact {
	object Stromkar extends Artifact(128910, "Strom'kar, the Warbreaker", (Iron, Blood, Shadow))
}
