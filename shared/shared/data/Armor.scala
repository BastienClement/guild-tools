package data

import utils.annotation.data

@data
sealed abstract class Armor(val name: String)

object Armor {
	object Cloth extends Armor("Cloth")
	object Leather extends Armor("Leather")
	object Mail extends Armor("Mail")
	object Plate extends Armor("Plate")
}
