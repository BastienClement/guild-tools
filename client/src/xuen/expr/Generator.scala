package xuen.expr

import xuen.expr.Expression._

object Generator {
	implicit class ImplGen(val e: Expression) extends AnyVal {
		@inline def gen: String = Generator.gen(e)
	}

	def gen(e: Expression): String = e match {
		case Empty => ""
		case ImplicitReceiver => "<ctx>"

		case Chain(exprs) => exprs.map(gen).mkString("; ")
		case Conditional(cond, yes, no) => s"(${ cond.gen }) ? ${ yes.gen } : ${ no.gen }"

		case PropertyRead(receiver, name) => s"${ receiver.gen }.$name"
		case SafePropertyRead(receiver, name) => s"${ receiver.gen }?.$name"
		case MethodCall(receiver, name, args) => s"${ receiver.gen }.$name(${ args.map(gen).mkString(", ") })"
		case SafeMethodCall(receiver, name, args) => s"${ receiver.gen }?.$name(${ args.map(gen).mkString(", ") })"
		case PropertyWrite(receiver, name, value) => s"${ receiver.gen }.$name = ${ value.gen }"

		case KeyedRead(obj, key) => s"${ obj.gen }[${ key.gen }]"
		case KeyedWrite(obj, key, value) => s"${ obj.gen }[${ key.gen }] = ${ value.gen }"

		case FunctionCall(target, args) => s"${ target.gen }(${ args.map(gen).mkString(", ") })"
		case Pipe(expr, name, args) => s"${ expr.gen } | $name" + (if (args.nonEmpty) ":" + args.map(gen).mkString(":") else "")

		case Binary(op, lhs, rhs) => s"${ lhs.gen } $op ${ rhs.gen }"
		case Unary(op, operand) => s"$op${ operand.gen }"

		case SelectorQuery(selector) if selector.head == '#' => selector
		case SelectorQuery(selector) => s"@$selector"

		case LiteralPrimitive(null) => "null"
		case LiteralPrimitive(bool: Boolean) => if (bool) "true" else "false"
		case LiteralPrimitive(value: String) => '"' + value + '"'
		case LiteralPrimitive(other) => other.toString

		case LiteralArray(values) => s"[${ values.map(gen).mkString(", ") }]"
		case LiteralMap(keys, values) => s"{${ keys.zip(values.map(gen)).map { case (k, v) => s"$k: $v" }.mkString(", ") }}"

		case Interpolation(_) => "<interpolation>"
		case _: Enumerator => "<enumerator>"

		case Reactive(expression) => s"Rx { ${ expression.gen } }"

		case other => other.toString
	}
}
