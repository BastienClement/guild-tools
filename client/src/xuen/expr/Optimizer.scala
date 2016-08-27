package xuen.expr

import scala.scalajs.js
import scala.scalajs.js.DynamicImplicits._
import utils.implicits._
import xuen.expr.Expression._

object Optimizer {
	/** Attempts to find known compile time value */
	private def compileTimeValue(expression: Expression): Option[Any] = expression match {
		case Empty =>
			Some(js.undefined)

		case Chain(exprs) =>
			if (exprs.isEmpty) Some(js.undefined) else compileTimeValue(exprs.last)

		case Conditional(cond, yes, no) =>
			compileTimeTruth(cond) match {
				case Some(true) => compileTimeValue(yes)
				case Some(false) => compileTimeValue(no)
				case None => for (y <- compileTimeValue(yes); n <- compileTimeValue(no); if y == n) yield y
			}

		case PropertyWrite(_, _, value) => compileTimeValue(value)
		case KeyedWrite(_, _, value) => compileTimeValue(value)
		case LiteralPrimitive(value) => Some(value)

		case Range(from, to) =>
			(compileTimeValue(from), compileTimeValue(to)) match {
				case (Some(a: Int), Some(b: Int)) => Some(a to b)
				case _ => None
			}

		case _ => None
	}

	/** Attempts to find known compile time truth value */
	private def compileTimeTruth(expression: Expression): Option[Boolean] = expression match {
		case Empty =>
			Some(false)

		case Chain(exprs) =>
			if (exprs.isEmpty) Some(false) else compileTimeTruth(exprs.last)

		case Conditional(cond, yes, no) if compileTimeTruth(cond).isEmpty =>
			for (y <- compileTimeTruth(yes); n <- compileTimeTruth(no); if y == n) yield y

		case PropertyWrite(_, _, value) => compileTimeTruth(value)
		case KeyedWrite(_, _, value) => compileTimeTruth(value)

		case Binary(op @ ("||" | "&&"), lhs, rhs) =>
			(op, compileTimeTruth(lhs), compileTimeTruth(rhs)) match {
				case ("||", _, Some(true)) | ("||", Some(true), _) => Some(true)
				case ("||", lhsTruth, Some(false)) => lhsTruth
				case ("||", Some(false), rhsTruth) => rhsTruth

				case ("&&", _, Some(false)) | ("&&", Some(false), _) => Some(false)
				case ("&&", lhsTruth, Some(true)) => lhsTruth
				case ("&&", Some(true), rhsTruth) => rhsTruth

				case _ => None
			}

		case LiteralArray(_) | LiteralMap(_, _) =>
			Some(true)

		case _ =>
			compileTimeValue(expression).map(_.dyn: Boolean)
	}

	/* An expression is pure if it does not have any visible side effects */
	private def pure(expression: Expression): Boolean = expression match {
		case Empty => true
		case Chain(exprs) => exprs.forall(pure)

		case Conditional(cond, yes, no) => pure(cond) && (compileTimeTruth(cond) match {
			case Some(true) => pure(yes)
			case Some(false) => pure(no)
			case None => pure(yes) && pure(no)
		})

		case Binary(_, lhs, rhs) => pure(lhs) && pure(rhs)
		case Unary(_, operand) => pure(operand)

		case LiteralArray(values) => values.forall(pure)
		case LiteralMap(_, values) => values.forall(pure)

		case Interpolation(fragments) => fragments.forall {
			case StringFragment(_) => true
			case ExpressionFragment(expr) => pure(expr)
		}

		case Range(from, to) => pure(from) && pure(to)

		case SelectorQuery(_) | LiteralPrimitive(_) => true

		case PropertyRead(_, _) | SafePropertyRead(_, _) |
		     MethodCall(_, _, _) | SafeMethodCall(_, _, _) |
		     PropertyWrite(_, _, _) |
		     KeyedRead(_, _) | KeyedWrite(_, _, _) |
		     FunctionCall(_, _) | Pipe(_, _, _) | Reactive(_) => false

		case _ => ???
	}

	/** Executes a binary operation at compile time */
	private def compileTimeBinary(op: String, lhs: js.Dynamic, rhs: js.Dynamic): Option[Any] = Some(op match {
		case "+" => lhs + rhs
		case "-" => lhs - rhs
		case "*" => lhs * rhs
		case "%" => lhs % rhs
		case "/" => lhs / rhs
		case "&&" => lhs && rhs
		case "||" => lhs || rhs
		case ">" => lhs > rhs
		case ">=" => lhs >= rhs
		case "<" => lhs < rhs
		case "<=" => lhs <= rhs
		case "==" => lhs == rhs
		case "!=" => lhs != rhs
		case "===" => lhs eq rhs
		case "!==" => lhs ne rhs
		case _ => return None
	})

	/** Executes a unary operation at compile time */
	private def compileTimeUnary(op: String, operand: js.Dynamic): Option[Any] = Some(op match {
		case "+" => operand.unary_+()
		case "-" => operand.unary_-()
		case "!" => operand.unary_!()
		case _ => return None
	})

	/** Joins sequential StringFragments in the interpolation */
	private def flattenInterpolation(fragments: List[InterpolationFragment]): List[InterpolationFragment] = fragments match {
		case Nil | (_ :: Nil) => fragments
		case StringFragment(a) :: StringFragment(b) :: tail => flattenInterpolation(StringFragment(a + b) :: tail)
		case head :: tail => head :: flattenInterpolation(tail)
	}

	/** Optimize the given expression */
	def optimize(expression: Expression): Expression = expression match {
		case Chain(exprs) if exprs.length < 1 =>
			Empty

		case Chain(exprs) =>
			val optExprs = exprs.map(optimize)
			Chain(optExprs.dropRight(1).filter(!pure(_)) :+ optExprs.last)

		case Conditional(cond, yes, no) =>
			val optCond = optimize(cond)
			val optYes = optimize(yes)
			val optNo = optimize(no)
			compileTimeTruth(optCond) match {
				case Some(true) => if (pure(optCond)) optYes else Chain(Seq(optCond, optYes))
				case Some(false) => if (pure(optCond)) optNo else Chain(Seq(optCond, optNo))
				case None => Conditional(optCond, optYes, optNo)
			}

		case PropertyRead(receiver, name) => PropertyRead(optimize(receiver), name)
		case SafePropertyRead(receiver, name) => SafePropertyRead(optimize(receiver), name)
		case MethodCall(receiver, name, args) => MethodCall(optimize(receiver), name, args.map(optimize))
		case SafeMethodCall(receiver, name, args) => SafeMethodCall(optimize(receiver), name, args.map(optimize))
		case PropertyWrite(receiver, name, value) => PropertyWrite(optimize(receiver), name, optimize(value))
		case KeyedRead(obj, key) => KeyedRead(optimize(obj), optimize(key))
		case KeyedWrite(obj, key, value) => KeyedWrite(optimize(obj), optimize(key), optimize(value))
		case FunctionCall(target, args) => FunctionCall(optimize(target), args.map(optimize))
		case Pipe(expr, name, args) => Pipe(optimize(expr), name, args.map(optimize))

		case Binary(op, lhs, rhs) =>
			val optLhs = optimize(lhs)
			val optRhs = optimize(rhs)
			(op, compileTimeTruth(optLhs), compileTimeTruth(optRhs)) match {
				case ("||", Some(true), _) => optLhs
				case ("||", Some(false), _) if pure(optLhs) => optRhs
				case ("&&", Some(false), _) => optLhs
				case ("&&", Some(true), _) if pure(optLhs) => optRhs
				case _ =>
					(for {
						left <- compileTimeValue(optLhs)
						right <- compileTimeValue(optRhs)
						value <- compileTimeBinary(op, left.dyn, right.dyn)
					} yield LiteralPrimitive(value)).getOrElse(Binary(op, optLhs, optRhs))
			}


		case Unary(op, operand) =>
			val optOperand = optimize(operand)
			(for {
				value <- compileTimeValue(operand)
				result <- compileTimeUnary(op, value.dyn)
			} yield LiteralPrimitive(result)).getOrElse(Unary(op, optOperand))

		case range: Range => compileTimeValue(range) match {
			case Some(r) => LiteralPrimitive(r)
			case None => range
		}

		case LiteralArray(values) => LiteralArray(values.map(optimize))
		case LiteralMap(keys, values) => LiteralMap(keys, values.map(optimize))

		case Interpolation(fragments) =>
			flattenInterpolation(fragments.map {
				case f @ StringFragment(_) => f
				case ExpressionFragment(expr) =>
					val optExpr = optimize(expr)
					compileTimeValue(optExpr) match {
						case Some(value) if pure(optExpr) => StringFragment(value.toString)
						case _ => ExpressionFragment(optExpr)
					}
			}.toList) match {
				case StringFragment(value) :: Nil => LiteralPrimitive(value)
				case ExpressionFragment(expr) :: Nil => expr
				case frags => Interpolation(frags)
			}

		case Enumerator(index, key, iterable, by, filter, locals) =>
			Enumerator(index, key, optimize(iterable), by.map(optimize), filter.map(optimize), locals.map(optimize))

		case Reactive(expr) => Reactive(optimize(expr))

		case _ => expression
	}
}
