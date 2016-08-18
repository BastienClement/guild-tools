package xuen.expr

import scala.annotation.tailrec
import scala.util.{Success, Try}
import xuen.XuenException

class Lexer(val input: String, val from: Int = 0) {
	private[this] val chars = input.toCharArray
	private[this] val length = chars.length
	private[this] var peek: Char = '\u0000'
	private[this] var next: Char = '\u0000'
	private[this] var index: Int = from - 1

	advance()

	@inline
	@tailrec private final def advance(count: Int = 1): Unit = {
		index += 1
		if (count > 1) advance(count - 1)
		else {
			peek = if (index >= length) '\u0000' else chars(index)
			next = if (index + 1 >= length) '\u0000' else chars(index + 1)
		}
	}

	@inline
	@tailrec private final def skipWhitespaces(): Unit = if (peek < ' ' && peek != '\u0000') {
		advance()
		skipWhitespaces()
	}

	@tailrec final def scanToken(): Token = {
		skipWhitespaces()

		if (index >= length) {
			Token.EOF
		} else if (index + 1 < length && peek == '}' && chars(index + 1) == '}') {
			Token.EOF
		} else if (isIdentifierStart(peek)) {
			scanIndentifier()
		} else if (peek.isDigit) {
			scanNumber()
		} else {
			implicit val start = index
			peek match {
				case '.' =>
					advance()
					if (peek.isDigit) scanNumber() else Token.Character('.')

				case '(' | ')' | '{' | '}' | '[' | ']' | ',' | ';' =>
					scanCharacter(peek)

				case '\'' | '"' =>
					scanString()

				case '#' | '@' | '+' | '*' | '/' | '%' | '^' =>
					scanOperator(peek)

				case ':' =>
					scanComplexOperator(':', '=')

				case '-' =>
					scanComplexOperator('-', '>')

				case '?' =>
					scanComplexOperator('?', '.')

				case '<' if next == '-' =>
					scanComplexOperator('<', '-')

				case '<' | '>' =>
					scanComplexOperator(peek, '=')

				case '!' | '=' =>
					scanComplexOperator(peek, '=', '=')

				case '&' | '|' =>
					scanComplexOperator(peek, peek)

				case c if c.isWhitespace =>
					advance()
					scanToken()

				case _ =>
					error(s"Unexpected character [$peek]")
			}
		}
	}

	@inline private def isIdentifierStart(peek: Char): Boolean = {
		peek.isLetter || peek == '_' || peek == '$'
	}

	@inline private def isIdentifierPart(peek: Char): Boolean = isIdentifierStart(peek) || peek.isDigit || peek == '-'
	@inline private def isExponentStart(peek: Char): Boolean = peek == 'e' || peek == 'E'
	@inline private def isExponentSign(peek: Char): Boolean = peek == '+' || peek == '-'

	private def scanIndentifier()(implicit start: Int = index): Token = {
		advance()
		while (isIdentifierPart(peek)) advance()

		String.valueOf(chars, start, index - start) match {
			case kw @ ("val" | "null" | "undefined" | "true" | "false" | "if" | "then" | "else" | "of" | "by" | "to") =>
				Token.Keyword(kw)

			case id =>
				Token.Identifier(id)
		}
	}

	private def scanNumber()(implicit start: Int = index): Token = {
		var simple = index == start
		advance()

		@tailrec def scanNumber(): Unit = {
			if (peek.isDigit) {
				// Do nothing
			} else if (peek == '.') {
				simple = false
			} else if (isExponentStart(peek)) {
				advance()
				if (isExponentSign(peek)) advance()
				if (!peek.isDigit) error("Invalid exponent", -1)
				simple = false
			} else {
				return
			}

			advance()
			scanNumber()
		}

		scanNumber()

		val str = String.valueOf(chars, start, index - start)
		val value: Double = if (simple) Integer.parseInt(str) else java.lang.Double.parseDouble(str)
		Token.Number(value)
	}

	private def scanCharacter(peek: Char)(implicit start: Int = index): Token = {
		advance()
		Token.Character(peek)
	}

	private def scanString()(implicit start: Int = index): Token = {
		val quote = peek
		advance()

		var buffer: StringBuilder = null
		var marker = index

		while (peek != quote) peek match {
			case '\\' =>
				if (buffer == null) buffer = StringBuilder.newBuilder
				buffer.append(String.valueOf(chars, marker, index - marker))
				advance()

				val unescaped = peek match {
					case 'u' =>
						// 4 character hex code for unicode character.
						val hex = String.valueOf(chars, index + 1, 4)
						Try {
							Integer.parseInt(hex, 16).toChar
						}.transform(
							res => { advance(4); Success(res) },
							fail => error(s"Invalid unicode escape [\\u$hex]")
						)

					case 'n' => '\n'
					case 'f' => '\f'
					case 'r' => '\r'
					case 't' => '\t'
					case code => code
				}
				advance()

				buffer.append(unescaped)
				marker = index

			case '\u0000' =>
				error("Unterminated string")

			case _ =>
				advance()
		}

		val last = String.valueOf(chars, marker, index - marker)
		advance()

		val str = if (buffer != null) {
			buffer.append(last)
			buffer.toString
		} else last

		Token.String(str)
	}

	private def scanOperator(peek: Char)(implicit start: Int = index): Token = {
		advance()
		Token.Operator(peek.toString)
	}

	private def scanComplexOperator(one: Char, two: Char, three: Char = '\u0000')(implicit start: Int = index): Token = {
		advance()
		var str = one.toString

		if (peek == two) {
			advance()
			str += two.toString
		}

		if (three != '\u0000' && peek == three) {
			advance()
			str += three.toString
		}

		Token.Operator(str)
	}

	private def error(message: String, offset: Int = 0): Nothing = {
		val position = index + offset
		throw XuenException(s"Lexer Error: $message at column $position in expression [$input]")
	}
}

object Lexer {
	def tokenize(input: String, from: Int = 0): Array[Token] = {
		val lexer = new Lexer(input, from)
		Stream.continually(lexer.scanToken()).takeWhile(_ != Token.EOF).toArray
	}
}
