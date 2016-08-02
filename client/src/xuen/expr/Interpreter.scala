package xuen.expr

import org.scalajs.dom.console
import scala.scalajs.js
import scala.scalajs.js.DynamicImplicits._
import scala.scalajs.js.JSConverters._
import scala.util.{Failure, Try}
import util.implicits._
import xuen.Context
import xuen.expr.Expression._
import xuen.rx.{Rx, Var}

object Interpreter {
	implicit class ImplicitEvaluator(private val expr: Expression) extends AnyVal {
		@inline def evaluate(implicit context: Context): Any = Interpreter.evaluate(expr, context)
	}

	private implicit class ImplicitUtils(private val receiver: Any) extends AnyVal {
		@inline def norm: Any = receiver match {
			case rx: Rx[_] => rx.!
			case other => other
		}

		@inline def read(name: String): Any = receiver match {
			case ctx: Context => ctx.get(name).norm
			case other => receiver.dyn.selectDynamic(name).norm
		}

		@inline def write(name: String, value: Any): Any = {
			(receiver match {
				case ctx: Context => ctx.get(name)
				case other => receiver.dyn.selectDynamic(name)
			}) match {
				case rx: Var[_] => rx.asInstanceOf[Var[Any]] := value
				case _ =>
					receiver match {
						case ctx: Context => ctx.set(name, value)
						case other => receiver.dyn.updateDynamic(name)(value.asInstanceOf[js.Any])
					}
			}
			value
		}

		@inline def call(name: String, args: Seq[Any]): Any = receiver match {
			case ctx: Context => ctx.invoke(name, args)
			case other =>
				val r = receiver.dyn
				r.selectDynamic(name).as[js.Function].call(r, args.asInstanceOf[Seq[js.Any]]: _*)
		}
	}

	private def evaluatePipe(expression: Expression, name: String, args: Seq[Expression])(implicit context: Context): Any = {
		val effectiveValue = expression.evaluate.norm
		val effectiveArgs = args.map { a => a.evaluate.norm }
		PipesRegistry.invoke(name, effectiveValue, effectiveArgs)
	}

	private def evaluateChain(expressions: Seq[Expression])(implicit context: Context): Any = {
		expressions.reduceLeft { (a: Expression, b: Expression) => a.evaluate; b }.evaluate.norm
	}

	private def evaluateConditional(cond: Expression, yes: Expression, no: Expression)(implicit context: Context): Any = {
		if (cond.evaluate.norm.dyn) yes.evaluate.norm else no.evaluate.norm
	}

	private def evaluatePropertyRead(receiver: Expression, name: String, unsafe: Boolean)(implicit context: Context): Any = {
		val effectiveReceiver = receiver.evaluate.norm
		if (unsafe || effectiveReceiver.?) effectiveReceiver.read(name)
		else js.undefined
	}

	private def evaluateMethodCall(receiver: Expression, name: String, args: Seq[Expression], unsafe: Boolean)(implicit context: Context): Any = {
		val effectiveReceiver = receiver.evaluate.norm
		if (unsafe || effectiveReceiver.?) {
			val effectiveArgs = args.map { a => a.evaluate.norm }
			effectiveReceiver.call(name, effectiveArgs)
		} else {
			js.undefined
		}
	}

	private def evaluatePropertyWrite(receiver: Expression, name: String, value: Expression)(implicit context: Context): Any = {
		val effectiveValue = value.evaluate.asInstanceOf[js.Any]
		receiver.evaluate.norm.write(name, effectiveValue)
	}

	private def evaluateKeyedRead(obj: Expression, key: Expression)(implicit context: Context): Any = {
		obj.evaluate.norm.dyn.selectDynamic(key.evaluate.norm.toString)
	}

	private def evaluateKeyedWrite(obj: Expression, key: Expression, value: Expression)(implicit context: Context): Any = {
		val effectiveValue = value.evaluate.asInstanceOf[js.Any]
		obj.evaluate.norm.dyn.updateDynamic(key.evaluate.norm.toString)(effectiveValue)
		effectiveValue
	}

	private def evaluateBinary(op: String, lhs: Expression, rhs: Expression)(implicit context: Context): Any = {
		@inline def effectiveLhs = lhs.evaluate.norm.dyn
		@inline def effectiveRhs = rhs.evaluate.norm.dyn
		(op match {
			case "+" => effectiveLhs + effectiveRhs
			case "-" => effectiveLhs - effectiveRhs
			case "*" => effectiveLhs * effectiveRhs
			case "%" => effectiveLhs % effectiveRhs
			case "/" => effectiveLhs / effectiveRhs
			case "<" => effectiveLhs < effectiveRhs
			case "<=" => effectiveLhs <= effectiveRhs
			case ">" => effectiveLhs > effectiveRhs
			case ">=" => effectiveLhs >= effectiveRhs
			case "==" => effectiveLhs == effectiveRhs
			case "!=" => effectiveLhs != effectiveRhs
			case "===" => effectiveLhs eq effectiveRhs
			case "!==" => effectiveLhs ne effectiveRhs
			case "&&" => effectiveLhs && effectiveRhs
			case "||" => effectiveLhs || effectiveRhs
		}).norm
	}

	private def evaluateUnary(op: String, operand: Expression)(implicit context: Context): Any = {
		val effectiveOperand = operand.evaluate.norm.dyn
		(op match {
			case "+" => effectiveOperand.unary_+()
			case "-" => effectiveOperand.unary_-()
			case "!" => effectiveOperand.unary_!()
		}).norm
	}

	private def evaluateSelectorQuery(selector: String)(implicit context: Context): Any = {
		context.selectElement(selector)
	}

	private def evaluateLiteralArray(values: Seq[Expression])(implicit context: Context): Any = {
		values.map { value => value.evaluate.norm.asInstanceOf[js.Any] }.toJSArray
	}

	private def evaluateLiteralMap(keys: Seq[String], values: Seq[Expression])(implicit context: Context): Any = {
		keys.zip(values.map { value => value.evaluate.norm.asInstanceOf[js.Any] }).toMap.toJSDictionary
	}

	private def evaluateInterpolation(fragments: Seq[InterpolationFragment])(implicit context: Context): Any = {
		fragments.collect {
			case StringFragment(str) => str
			case ExpressionFragment(e) =>
				val value = e.evaluate
				if (value == null) "null"
				else value.toString
		}.mkString
	}

	private def evaluateReactive(expression: Expression)(implicit context: Context): Any = {
		Rx { expression.evaluate }
	}

	def evaluate(implicit expr: Expression, context: Context): Any = expr match {
		case Empty => js.undefined
		case ImplicitReceiver => context

		case Pipe(expression, name, args) => evaluatePipe(expression, name, args)
		case Chain(expressions) => evaluateChain(expressions)
		case Conditional(cond, yes, no) => evaluateConditional(cond, yes, no)
		case PropertyRead(receiver, name) => evaluatePropertyRead(receiver, name, true)
		case SafePropertyRead(receiver, name) => evaluatePropertyRead(receiver, name, false)
		case MethodCall(receiver, name, args) => evaluateMethodCall(receiver, name, args, true)
		case SafeMethodCall(receiver, name, args) => evaluateMethodCall(receiver, name, args, false)
		case PropertyWrite(receiver, name, value) => evaluatePropertyWrite(receiver, name, value)
		case KeyedRead(obj, key) => evaluateKeyedRead(obj, key)
		case KeyedWrite(obj, key, value) => evaluateKeyedWrite(obj, key, value)
		case Binary(op, lhs, rhs) => evaluateBinary(op, lhs, rhs)
		case Unary(op, operand) => evaluateUnary(op, operand)
		case SelectorQuery(id) => evaluateSelectorQuery(id)
		case LiteralPrimitive(value) => value
		case LiteralArray(values) => evaluateLiteralArray(values)
		case LiteralMap(keys, values) => evaluateLiteralMap(keys, values)
		case Interpolation(fragments) => evaluateInterpolation(fragments)
		case Reactive(expression) => evaluateReactive(expression)

		case other =>
			val e = s"Evaluating unsupported expression: $expr"
			console.warn(e)
			e
	}

	def safeEvaluate(expr: Expression, context: Context): Any = Try {
		evaluate(expr, context)
	}.recover {
		case fail: Throwable =>
			console.error("Error while evaluating expression:\n\n" + expr.toString + "\n\n>> " + fail.toString)
			Failure(fail)
	}.getOrElse(js.undefined)
}
