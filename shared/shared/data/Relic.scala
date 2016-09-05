package data

import utils.annotation.data

@data
sealed abstract class Relic(val name: String)

object Relic {
	object Arcane extends Relic("Arcane")
	object Blood extends Relic("Blood")
	object Fel extends Relic("Fel")
	object Fire extends Relic("Fire")
	object Frost extends Relic("Frost")
	object Holy extends Relic("Holy")
	object Iron extends Relic("Iron")
	object Life extends Relic("Life")
	object Shadow extends Relic("Shadow")
	object Water extends Relic("Water")
	object Wind extends Relic("Wind")
}
