package xuen.expr

trait Token {
	val position: Int
}

object Token {
	import scala.Predef.{String => ScalaString}

	case class Character(char: Char)(implicit val position: Int) extends Token
	case class Identifier(identifier: ScalaString)(implicit val position: Int) extends Token
	case class Keyword(keyword: ScalaString)(implicit val position: Int) extends Token
	case class String(string: ScalaString)(implicit val position: Int) extends Token
	case class Operator(operator: ScalaString)(implicit val position: Int) extends Token
	case class Number(number: Double)(implicit val position: Int) extends Token

	case object EOF extends Token {
		val position = -1
	}
}


