package data

import utils.annotation.data

@data
sealed abstract class Token(val name: String)

object Token {
	object Conqueror extends Token("Conqueror")
	object Protector extends Token("Protector")
	object Vanquisher extends Token("Vanquisher")
}
