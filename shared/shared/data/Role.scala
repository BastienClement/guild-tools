package data

import utils.annotation.data

@data
sealed abstract class Role(val key: String)

object Role {
	object Tank extends Role("TANK")
	object Healing extends Role("HEALING")
	object DPS extends Role("DPS")
	object Unknown extends Role("UNKNOW")
}
