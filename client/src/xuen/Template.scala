package xuen

import facade.HTMLTemplateElement
import facade.ShadowDOM._
import org.scalajs.dom._
import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.DynamicImplicits._
import util.Implicits._
import util.Serializer
import xuen.expr.Expression.LiteralPrimitive
import xuen.expr.{Expression, Interpreter, Parser}
import xuen.rx.syntax.ExplicitExtractor
import xuen.rx.{Obs, Rx, Var}

/**
  * A component template, defined by a <template> tag.
  */
case class Template(template: HTMLTemplateElement) {
	private type BindingBuilder[N <: Node] = (N, Context) => Option[(Rx[_], Obs)]
	private type BindingAdapter = (Element) => Node

	private[this] val bindingDefinitions = mutable.Map.empty[String, (BindingAdapter, mutable.Set[BindingBuilder[Node]])]

	template.content.normalize()
	compile(template.content)

	/** Removes the xuen-bindings attribute tag */
	private def elementBindingsAdapter(element: Element): Node = {
		element.removeAttribute("xuen-bindings")
		element
	}

	/** Registers a binding on an Element node */
	private def registerBinding[E <: Element](element: E)(builder: BindingBuilder[E]): Unit = {
		val bindingId = Option(element.getAttribute("xuen-bindings")).getOrElse({
			val id = Template.nextBindingId
			element.setAttribute("xuen-bindings", id)
			bindingDefinitions.put(id, (elementBindingsAdapter, mutable.Set.empty))
			id
		})
		bindingDefinitions(bindingId)._2.add(builder.asInstanceOf[BindingBuilder[Node]])
	}

	/** Registers a binding on an Element node */
	private def registerBindingExpr[E <: Element](element: E, expr: Expression)(behavior: (E, Any) => Unit): Unit = {
		registerBinding(element) { case (elem, context) =>
			val rx = Rx { Interpreter.safeEvaluate(expr, context) }
			val obs = Obs { behavior(elem, rx.!) }
			Some((rx, obs))
		}
	}

	/** Alias for registerBindingExpr with an expression source code */
	private def registerBindingExpr[E <: Element](element: E, exprSource: String)(behavior: (E, Any) => Unit): Unit = {
		registerBindingExpr(element, Parser.parseExpression(exprSource))(behavior)
	}

	/** Reverts the <xuen-interpolation> placeholder to a text node */
	private def textBindingsAdapter(element: Element): Node = {
		val text = element.ownerDocument.createTextNode("")
		element.parentNode.replaceChild(text, element)
		text
	}

	/**
	  * Registers a binding on a Text node.
	  * Unlike for elements, only a single binding can be defined on a text node.
	  */
	private def registerBinding(text: Text)(builder: BindingBuilder[Text]): Unit = {
		val id = Template.nextBindingId
		val synthElement = text.ownerDocument.createElement("xuen-interpolation")
		synthElement.setAttribute("xuen-bindings", id)
		text.parentNode.replaceChild(synthElement, text)
		bindingDefinitions.put(id, (textBindingsAdapter, mutable.Set(builder.asInstanceOf[BindingBuilder[Node]])))
	}

	/** Transforms an hyphenated string to a camel-cased one */
	private def toCamelCase(orig: String): String = {
		"""([a-z])\-([a-z])""".r.replaceAllIn(orig, m => m.group(1) + m.group(2).toUpperCase)
	}

	/** Compiles a sub-tree of the template */
	//noinspection UnitMethodIsParameterless
	private final def compile(node: Node): Unit = node match {
		case element: Element => compileElement(element)
		case text: Text => compileTextNode(text)
		case fragment: DocumentFragment => node.childNodes.foreach(compile)
		case comment: Comment => // Ignore
		case unknown => throw XuenException(s"Encountered unknown node type '$unknown' while compiling template")
	}

	/** Compiles an Element node */
	private def compileElement(element: Element): Unit = {
		if (element.hasAttributes) {
			for {
				attribute <- for (attr <- element.attributes) yield attr.asInstanceOf[Attr]
				if !Template.attrBlacklist.contains(attribute.name)
			} compileAttribute(element, attribute)
		}
      element.childNodes.foreach(compile)
	}

	/** Compiles an element's attribute */
	private def compileAttribute(element: Element, attribute: Attr): Unit = {
		val name = attribute.name
		val value = attribute.value

		val last = name.last
		name.head match {
			case '#' => compileIdSugarAttribute(element, name)
			case '.' => compileShortClassBindingAttribute(element, name, value)
			case '[' if last == ']' => compileDataBindingAttribute(element, name, value)
			case '(' if last == ')' => compileEventListenerAttribute(element, name, value)
			case _ => compileGenericAttribute(element, name, value)
		}
	}

	/** Compiles an ID sugar attribute */
	private def compileIdSugarAttribute(element: Element, name: String): Unit = {
		element.removeAttribute(name)
		element.setAttribute("id", name.substring(1))
	}

	/** Compiles a short class binding attribute */
	private def compileShortClassBindingAttribute(element: Element, name: String, value: String): Unit = {
		element.removeAttribute(name)
		val className = name.substring(1)

		// Check if we have a condition associated with this class
		Option(value).filter(v => v.trim.length > 0) match {
			case Some(cond) =>
				registerBindingExpr(element, cond) { (elem, value) =>
					if (value.dyn) elem.classList.add(className)
					else elem.classList.remove(className)
				}

			case None =>
				element.classList.add(className)
		}
	}

	/** Compiles a data binding attribute */
	private def compileDataBindingAttribute(element: Element, name: String, value: String): Unit = {
		element.removeAttribute(name)
		val target = name.substring(1, name.length - 1)
		target.head match {
			case '@' => compileAttributeBindingAttribute(element, target, value)
			case '$' => compileStyleBindingAttribute(element, target, value)
			case '€' => compileClassBindingAttribute(element, target, value)
			case _ => compilePropertyBindingAttribute(element, target, value)
		}
	}

	/** Compiles an attribute binding attribute */
	private def compileAttributeBindingAttribute(element: Element, target: String, value: String): Unit = {
		val attribute = target.substring(1)
		registerBindingExpr(element, value) { (elem, value) =>
			Serializer.forValue(value).write(value) match {
				case None => elem.removeAttribute(attribute)
				case Some(string) => elem.setAttribute(attribute, string)
			}
		}
	}

	/** Compiles a style binding attribute */
	private def compileStyleBindingAttribute(element: Element, target: String, value: String): Unit = {
		val style = toCamelCase(target.substring(1))
		registerBindingExpr(element, value) { (elem, value) =>
			elem.dyn.style.updateDynamic(style)(value.toString)
		}
	}

	/** Compiles a class binding attribute */
	private def compileClassBindingAttribute(element: Element, target: String, value: String): Unit = {
		val className = toCamelCase(target.substring(1))
		registerBindingExpr(element, value) { (elem, value) =>
			if (value.dyn) elem.classList.add(className)
			else elem.classList.remove(className)
		}
	}

	/** Compiles a property binding attribute */
	private def compilePropertyBindingAttribute(element: Element, target: String, value: String): Unit = {
		registerBindingExpr(element, value) { (elem, value) =>
			elem.dyn.selectDynamic(target) match {
				case rx: Var[Any] => rx := value
				case _ => elem.dyn.updateDynamic(target)(value.asInstanceOf[js.Any])
			}
		}
	}

	/** Compiles an event listener attribute */
	private def compileEventListenerAttribute(element: Element, name: String, value: String): Unit = {
		element.removeAttribute(name)
		val target = name.substring(1, name.length - 1)
		val expr = Parser.parseExpression(value)
		registerBinding(element) { case (elem, context) =>
			val dispatchContext = context.child()
			elem.addEventListener(target, (event: Event) => {
				dispatchContext.set("$event", event)
				Interpreter.safeEvaluate(expr, dispatchContext)
			})
			None
		}
	}

	/** Compiles a generic attribute interpolation */
	private def compileGenericAttribute(element: Element, name: String, value: String): Unit = {
		for (expr <- Parser.parseInterpolation(value)) {
			element.removeAttribute(name)
			registerBindingExpr(element, expr) { (elem, value) =>
				Serializer.forValue(value).write(value) match {
					case None => elem.removeAttribute(name)
					case Some(string) => elem.setAttribute(name, string)
				}
			}
		}
	}

	/** Compiles a Text node */
	private def compileTextNode(text: Text): Unit = {
		Parser.parseInterpolation(text.data).foreach {
			case LiteralPrimitive(value) =>
				text.data = value.toString

			case expr =>
				registerBinding(text) { case (elem, context) =>
					val rx = Rx { Interpreter.safeEvaluate(expr, context) }
					val obs = Obs { elem.data = rx.!.toString }
					Some((rx, obs))
				}
		}
	}

	/**
	  * An instance of the template.
	  */
	class Instance private[Template] (val context: Context) {
		/** The root node of this template */
		val root = template.content.cloneNode(true).as[DocumentFragment]

		/** The list of data-bindings defined for this template */
		private[this] val bindings = mutable.Set.empty[(Rx[_], Obs)]

		/** The current state of the template bindings */
		private[this] var enabled = false

		/** Initialize the template instance */
		for {
			target <- root.querySelectorAll("[xuen-bindings]").as[NodeListOf[Element]]
			id = target.getAttribute("xuen-bindings")
			(adapater, builders) = bindingDefinitions(id)
		} {
			val node = adapater.apply(target)
			for (builder <- builders) {
				for (binding <- builder.apply(node, context)) {
					bindings.add(binding)
				}
			}
		}

		/**
		  * Enables template bindings.
		  * The data-bound nodes in the template will start reflecting changes
		  * from source reactive variables.
		  */
		def enable(): Unit = if (!enabled) {
			Rx.atomically {
				for ((v, o) <- bindings) v ~>> o
			}
			enabled = false
		}

		/**
		  * Disables template bindings.
		  * Every nodes data-bound to a reactive variable will have its observer
		  * unbound from the variable and the template will stop updating.
		  */
		def disable(): Unit = if (enabled) {
			for ((v, o) <- bindings) v ~!> o
			enabled = false
		}
	}

	/** Instanciate this template and binds it to the given context */
	def bind(context: Context): Instance = new Instance(context)

	/** Stamps this template on the given handler element */
	def stamp(component: ComponentInstance): Instance = {
		val instance = bind(Context.ref(component))
		component.createShadowRoot().appendChild(instance.root)
		instance
	}
}

object Template {
	/** Id of the last Xuen binding registered */
	private[this] var lastBindingId = 0

	private[Template] def nextBindingId: String = {
		lastBindingId += 1
		Integer.toHexString(lastBindingId)
	}

	val attrBlacklist = Set("class", "style")
}
