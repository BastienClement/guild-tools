package xuen.expr

sealed trait Expression

object Expression {
	case object Empty extends Expression
	case object ImplicitReceiver extends Expression

	case class Chain(exprs: Seq[Expression]) extends Expression
	case class Conditional(cond: Expression, yes: Expression, no: Expression) extends Expression

	case class PropertyRead(receiver: Expression, name: String) extends Expression
	case class SafePropertyRead(receiver: Expression, name: String) extends Expression
	case class MethodCall(receiver: Expression, name: String, args: Seq[Expression]) extends Expression
	case class SafeMethodCall(receiver: Expression, name: String, args: Seq[Expression]) extends Expression
	case class PropertyWrite(receiver: Expression, name: String, value: Expression) extends Expression

	case class KeyedRead(obj: Expression, key: Expression) extends Expression
	case class KeyedWrite(obj: Expression, key: Expression, value: Expression) extends Expression

	case class FunctionCall(target: Expression, args: Seq[Expression]) extends Expression
	case class Pipe(expr: Expression, name: String, args: Seq[Expression]) extends Expression

	case class Binary(op: String, lhs: Expression, rhs: Expression) extends Expression
	case class Unary(op: String, operand: Expression) extends Expression

	case class Range(from: Expression, to: Expression) extends Expression
	case class SelectorQuery(selector: String) extends Expression

	case class LiteralPrimitive(value: Any) extends Expression
	case class LiteralArray(values: Seq[Expression]) extends Expression
	case class LiteralMap(keys: Seq[String], values: Seq[Expression]) extends Expression

	case class Interpolation(fragments: Seq[InterpolationFragment]) extends Expression

	sealed trait InterpolationFragment
	case class StringFragment(value: String) extends InterpolationFragment
	case class ExpressionFragment(expression: Expression) extends InterpolationFragment

	case class Enumerator(index: Option[String], key: String, iterable: Expression, by: Option[Expression],
	                      filter: Option[Expression], locals: Option[Expression]) extends Expression {
		val indexKey = index.getOrElse("$key")
	}

	case class Reactive(expression: Expression) extends Expression
}
