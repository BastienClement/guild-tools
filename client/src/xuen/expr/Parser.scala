package xuen.expr
import scala.annotation.tailrec
import scala.scalajs.js
import xuen.XuenException
import xuen.expr.Expression._

//noinspection LoopVariableNotUpdated
class Parser(val input: String, private[this] val tokens: Array[Token]) {
	private[this] val length = tokens.length
	private[this] var index = 0

	@inline private def next: Token = peek(0)

	private def peek(offset: Int = 1): Token = {
		val pos = index + offset
		if (pos >= length) Token.EOF else tokens(pos)
	}

	private def advance(): Unit = {
		index += 1
	}

	private def optionalCharacter(char: Char): Boolean = next match {
		case Token.Character(c) if c == char => advance(); true
		case _ => false
	}

	private def expectCharacter(char: Char): Unit = {
		if (!optionalCharacter(char)) error(s"Missing expected '$char'")
	}

	private def optionalOperator(operator: String): Boolean = next match {
		case Token.Operator(op) if op == operator => advance(); true
		case _ => false
	}

	private def expectOperator(operator: String): Unit = {
		if (!optionalOperator(operator)) error(s"Missing expected operator '$operator'")
	}

	private def expectIdentifierOrKeyword: String = next match {
		case Token.Identifier(id) => advance(); id
		case Token.Keyword(kw) => advance(); kw
		case tok => error(s"Unexpected token $tok, expected identifier or keyword")
	}

	private def expectIdentifierOrKeywordOrString: String = next match {
		case Token.Identifier(id) => advance(); id
		case Token.Keyword(kw) => advance(); kw
		case Token.String(str) => advance(); str
		case tok => error(s"Unexpected token $tok, expected identifier or keyword or string")
	}

	private def peekKeywordLet: Boolean = next match {
		case Token.Keyword("let") => true
		case _ => false
	}

	@inline private def list = listOf[Expression]
	@inline private def listOf[T] = js.Array[T]()

	def chain: Expression = {
		val exprs = list

		while (index < length) {
			exprs.push(pipe)
			while (optionalCharacter(';')) {}
		}

		exprs.length match {
			case 0 => Empty
			case 1 => exprs(0)
			case _ => Chain(exprs)
		}
	}

	def pipe: Expression = {
		var res = expression
		if (optionalOperator("|")) {
			do {
				val name = expectIdentifierOrKeyword
				val args = list
				while (optionalCharacter(':')) args.push(expression)
				res = Pipe(res, name, args)
			} while (optionalOperator("|"))
		}
		res
	}

	def expression: Expression = conditional

	def conditional: Expression = {
		val res = logicalOr
		if (optionalOperator("?")) {
			val yes = pipe
			expectCharacter(':')
			val no = pipe
			Conditional(res, yes, no)
		} else {
			res
		}
	}

	def logicalOr: Expression = {
		var res = logicalAnd
		while (optionalOperator("||")) {
			res = Binary("||", res, logicalAnd)
		}
		res
	}

	def logicalAnd: Expression = {
		var res = equality
		while (optionalOperator("&&")) {
			res = Binary("&&", res, equality)
		}
		res
	}

	def equality: Expression = {
		@inline @tailrec def parse(lhs: Expression): Expression = next match {
			case Token.Operator(op @ ("==" | "===" | "!=" | "!==")) => advance(); parse(Binary(op, lhs, relational))
			case _ => lhs
		}
		parse(relational)
	}

	def relational: Expression = {
		@inline @tailrec def parse(lhs: Expression): Expression = next match {
			case Token.Operator(op @ ("<" | ">" | "<=" | ">=")) => advance(); parse(Binary(op, lhs, additive))
			case _ => lhs
		}
		parse(additive)
	}

	def additive: Expression = {
		@inline @tailrec def parse(lhs: Expression): Expression = next match {
			case Token.Operator(op @ ("+" | "-")) => advance(); parse(Binary(op, lhs, multiplicative))
			case _ => lhs
		}
		parse(multiplicative)
	}

	def multiplicative: Expression = {
		@inline @tailrec def parse(lhs: Expression): Expression = next match {
			case Token.Operator(op @ ("*" | "%" | "/")) => advance(); parse(Binary(op, lhs, prefix))
			case _ => lhs
		}
		parse(prefix)
	}

	final def prefix: Expression = next match {
		case Token.Operator(op @ ("+" | "-" | "!")) => advance(); Unary(op, prefix)
		case _ => callChain
	}

	def callChain: Expression = {
		var res = primary
		while (true) {
			if (optionalCharacter('.')) {
				res = accessMemberOrMethodCall(res, false)
			} else if (optionalOperator("?.")) {
				res = accessMemberOrMethodCall(res, true)
			} else if (optionalCharacter('[')) {
				val key = pipe
				expectCharacter(']')
				if (optionalOperator("=")) {
					res = KeyedWrite(res, key, expression)
				} else {
					res = KeyedRead(res, key)
				}
			} else if (optionalCharacter('(')) {
				res = FunctionCall(res, callArguments)
				expectCharacter(')')
			} else {
				return res
			}
		}
		???
	}

	def primary: Expression = {
		if (optionalCharacter('(')) {
			val exp = pipe
			expectCharacter(')')
			exp
		} else {
			next match {
				case Token.Keyword("null" | "undefined") => advance(); LiteralPrimitive(null)
				case Token.Keyword("true") => advance(); LiteralPrimitive(true)
				case Token.Keyword("false") => advance(); LiteralPrimitive(false)
				case Token.Number(value) => advance(); LiteralPrimitive(value)
				case Token.String(value) => advance(); LiteralPrimitive(value)
				case Token.Identifier(id) => accessMemberOrMethodCall(ImplicitReceiver, false)
				case Token.Character('[') => literalArray
				case Token.Character('{') => literalMap
				case _ if index >= length => error("Unexpected end of expression")
				case tok => error(s"Unexpected token $next")
			}
		}
	}

	def literalArray: Expression = {
		val values = list
		expectCharacter('[')
		if (!optionalCharacter(']')) {
			do {
				values.push(pipe)
			} while (optionalCharacter(','))
			expectCharacter(']')
		}
		LiteralArray(values)
	}

	def literalMap: Expression = {
		val keys = listOf[String]
		val values = list
		expectCharacter('{')
		if (!optionalCharacter('}')) {
			do {
				keys.push(expectIdentifierOrKeywordOrString)
				expectCharacter(':')
				values.push(pipe)
			} while (optionalCharacter(','))
			expectCharacter('}')
		}
		LiteralMap(keys, values)
	}

	def accessMemberOrMethodCall(receiver: Expression, safe: Boolean): Expression = {
		val id = expectIdentifierOrKeyword
		if (optionalCharacter('(')) {
			val args = callArguments
			expectCharacter(')')
			if (safe) SafeMethodCall(receiver, id, args)
			else MethodCall(receiver, id, args)
		} else {
			if (safe) {
				if (optionalOperator("=")) {
					error("The '?.' operator cannot be used in the assignment")
				} else {
					SafePropertyRead(receiver, id)
				}
			} else {
				if (optionalOperator("=")) {
					PropertyWrite(receiver, id, expression)
				} else {
					PropertyRead(receiver, id)
				}
			}
		}
	}

	def callArguments: Seq[Expression] = {
		next match {
			case Token.Character(')') => Seq.empty
			case _ =>
				val args = list
				do {
					args.push(pipe)
				} while (optionalCharacter(','))
				args
		}
	}

	private def error(message: String): Nothing = {
		throw XuenException(s"Parse Error: $message in '$input'")
	}
}

object Parser {
	def parseExpression(input: String, from: Int = 0): Expression = {
		val parser = new Parser(input, Lexer.tokenize(input, from))
		parser.chain
	}

	def parseInterpolation(input: String): Option[Expression] = {
		if (input.indexOf("{{") < 0) None
		else {
			val fragments = js.Array[InterpolationFragment]()

			var hasStringFragments = false
			var hasExpressionFragments = false

			@tailrec
			def parse(from: Int): Unit = {
				val begin = input.indexOf("{{", from)
				if (begin < 0) {
					if (from < input.length) {
						fragments.push(StringFragment(input.substring(from)))
						hasStringFragments = true
					}
				} else {
					if (begin != from) {
						fragments.push(StringFragment(input.substring(from, begin)))
						hasStringFragments = true
					}

					val end = input.indexOf("}}", from)
					if (end < 0) {
						throw XuenException(s"Unterminated interpolation expression in '${input.substring(begin)}'")
					}

					fragments.push(ExpressionFragment(parseExpression(input, begin + 2)))
					hasExpressionFragments = true

					parse(end + 2)
				}
			}

			parse(0)

			fragments.length match {
				case 0 => None
				case 1 if hasStringFragments => None
				case 1 if hasExpressionFragments => Some(fragments(0).asInstanceOf[ExpressionFragment].expression)
				case _ => Some(Interpolation(fragments))
			}
		}
	}
}
