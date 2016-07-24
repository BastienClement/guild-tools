package xuen

import facade.HTMLTemplateElement
import facade.ShadowDOM._
import org.scalajs.dom._
import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.DynamicImplicits._
import util.Serializer
import util.implicits._
import xuen.Template._
import xuen.expr.Expression.LiteralPrimitive
import xuen.expr.{Expression, Interpreter, Parser}
import xuen.rx.syntax.ExplicitExtractor
import xuen.rx.{Obs, Rx, Var}

/**
  * A component template, defined by a <template> tag.
  */
case class Template(template: HTMLTemplateElement) {
	// Ensure that the template as at least one child
	if (template.content.childNodes.length < 1) {
		template.content.appendChild(document.createComment(" empty template "))
	}

	template.content.normalize()
	compile(template.content)

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
		case comment: Comment => compileComment(comment)
		case unknown => throw XuenException(s"Encountered unknown node type '$unknown' while compiling template")
	}

	/** Compiles an Element node */
	private def compileElement(element: Element): Unit = {
		if (element.hasAttribute("*if")) compileIfTransformation(element)
		else if (element.hasAttribute("*for")) compileForTransformation(element)
		else if (element.hasAttribute("*scope")) compileScopeTransformation(element)
		else {
			if (element.hasAttributes) {
				for {
					attribute <- for (attr <- element.attributes) yield attr.asInstanceOf[Attr]
					if !Template.attrBlacklist.contains(attribute.name)
				} compileAttribute(element, attribute)
			}
			if (element.hasAttribute("*match")) compileMatchTransform(element)
			else element.childNodes.foreach(compile)
		}
	}

	/** Compiles a *if transformation */
	private def compileIfTransformation(element: Element): Unit = {
		val sourceExpr = element.getAttribute("*if")
		val expr = Parser.parseExpression(sourceExpr)
		element.removeAttribute("*if")

		templateWrap(element, s"*if $sourceExpr") { (placeholder, context, template, parent) =>
			var instance: Template#Instance = null
			var inserted: Node = null

			val rx = Rx { Interpreter.safeEvaluate(expr, context) }
			val obs = Obs {
				if (instance == null && rx.!.dyn) {
					instance = template.bind(context)
					inserted = instance.root.firstChild
					parent.attach(instance)
					placeholder.parentNode.insertBefore(instance.root, placeholder.nextSibling)
				} else if (instance != null && !rx.!.dyn) {
					parent.detach(instance)
					inserted.parentNode.removeChild(inserted)
					instance = null
					inserted = null
				}
			}
			Some((rx, obs))
		}
	}

	private def compileForTransformation(element: Element): Unit = {
		val sourceExpr = element.getAttribute("*for")
		val enumerator = Parser.parseEnumerator(sourceExpr)
		element.removeAttribute("*for")

		templateWrap(element, s"*for $sourceExpr") { (placeholder, context, template, parent) =>
			val rx = Rx {
				val items: Iterable[Any] = Interpreter.safeEvaluate(enumerator.iterable, context) match {
					case it: Iterable[_] => it
					case array: js.Array[_] => array
					case unsupported => throw XuenException(s"Unsupported iterable in for-loop: ${ unsupported.getClass.getName }")
				}

				val source: Iterable[(Any, Any)] = items match {
					case map: Map[_, _] => map
					case it: Iterable[_] => Stream.from(0).zip(it)
				}

				for {
					(index, value) <- source
					lazyCtx = {
						lazy val lzy = context.child(
							enumerator.key -> Var(value),
							enumerator.indexKey -> Var(index)
						)
						() => lzy
					}
					if enumerator.filter.map(Interpreter.evaluate(_, lazyCtx())).getOrElse(true).dyn
				} yield (index, value, lazyCtx)
			}

			case class ItemNode(ctx: Context) {
				var flag = false
				var fresh = true
				var prevRawIndex = ctx.get("$index")

				val tmpl = template.bind(ctx)
				val node = tmpl.root.firstChild

				parent.attach(tmpl)

				def dispose() = {
					parent.detach(tmpl)
					node.parentNode.removeChild(node)
				}
			}

			val nodes = mutable.Map.empty[Any, ItemNode]

			val obs = Obs {
				for ((_, in) <- nodes) in.flag = true

				var lastNode = placeholder

				val items = rx.!
				val lastIndex = items.size - 1
				var rawIndex = 0

				for ((index, value, lazyCtx) <- rx.!)  {
					// Instantiate the lazy context
					lazy val ctx = {
						val c = lazyCtx()
						c.set("$index", Var(rawIndex))
						c.set("$first", Var(rawIndex == 0))
						c.set("$even", Var(rawIndex % 2 == 0))
						c.set("$odd", Var(rawIndex % 2 == 1))
						c.set("$last", Var(rawIndex == lastIndex))
						c
					}

					// Fetch the discriminant key
					val key = enumerator.by.map { by => Interpreter.evaluate(by, ctx) }.filter(_ != js.undefined).getOrElse(value)

					// Fetch or create the item node
					val item = nodes.getOrElseUpdate(key, {
						enumerator.locals.foreach { locals => Interpreter.evaluate(locals, ctx) }
						ItemNode(ctx)
					})

					// Clear the dispose flag
					item.flag = false

					// Update old context if node was not recreated
					if (!item.fresh) {
						val old = item.ctx

						old.get(enumerator.key).asInstanceOf[Var[Any]] := value
						old.get(enumerator.indexKey).asInstanceOf[Var[Any]] := index

						if (item.prevRawIndex != rawIndex) {
							old.get("$index").asInstanceOf[Var[Int]] := rawIndex
							old.get("$first").asInstanceOf[Var[Boolean]] := (rawIndex == 0)
							old.get("$even").asInstanceOf[Var[Boolean]] := (rawIndex % 2 == 0)
							old.get("$odd").asInstanceOf[Var[Boolean]] := (rawIndex % 2 == 1)
							old.get("$last").asInstanceOf[Var[Boolean]] := (rawIndex == lastIndex)
						}
					} else {
						item.fresh = false
					}

					// Update the DOM
					if (lastNode.nextSibling ne item.node) {
						lastNode.parentNode.insertBefore(item.node, lastNode.nextSibling)
					}

					lastNode = item.node
					rawIndex += 1
				}

				for ((key, in) <- nodes if in.flag) {
					in.dispose()
					nodes.remove(key)
				}
			}

			Some((rx, obs))
		}
	}

	private def compileMatchTransform(element: Element): Unit = {
		val selector = element.getAttribute("*match")
		val expr = Parser.parseExpression(selector)
		element.removeAttribute("*match")

		type Case = (Option[Expression], Template)
		val cases = js.Array[Case]()

		@inline def wrapCase(element: Element, expr: Option[String]): Unit = {
			val template = document.createElement("template").as[HTMLTemplateElement]
			element.parentNode.replaceChild(template, element)
			template.content.appendChild(element)
			cases.push((expr.map(Parser.parseExpression(_)), Template(template)))
		}

		@inline def caseMatch(casee: Case, value: Any, context: Context): Boolean = casee._1 match {
			case None => true
			case Some(ref) => Interpreter.safeEvaluate(ref, context) match {
				case bool: Boolean => bool
				case res => res == value
			}
			case _ => false
		}

		var children = js.Array[Node]()
		for (child <- element.childNodes) children.push(child)

		children.foreach {
			case el: Element if el.hasAttribute("*case") =>
				val expr = Some(el.getAttribute("*case"))
				el.removeAttribute("*case")
				wrapCase(el, expr)

			case el: Element if el.hasAttribute("*default") =>
				el.removeAttribute("*default")
				wrapCase(el, None)

			case el: Element =>
				console.warn("*match: removing untagged element:", el)
				el.parentNode.removeChild(el)

			case node =>
				element.removeChild(node)
		}

		children = null
		val comment = document.createComment(s" *match $selector ")
		element.parentNode.insertBefore(comment, element)

		registerBinding(element) { case (node, context, instance) =>
			node.innerHTML = ""

			var current: Option[(Case, Template#Instance)] = None
			val rx = Rx { cases.find(caseMatch(_, Interpreter.safeEvaluate(expr, context), context)) }

			@inline def setCurrentCase(casee: Case): Unit = {
				val newChild = casee._2.bind(context)
				node.appendChild(newChild.root)
				instance.attach(newChild)
				current = Some((casee, newChild))
			}

			@inline def detachCurrent(): Unit = {
				for ((_, child) <- current) {
					instance.detach(child)
					node.removeChild(node.firstChild)
					current = None
				}
			}

			val obs = Obs {
				(rx.!, current) match {
					case (None, Some(_)) =>
						detachCurrent()

					case (Some(casee), None) =>
						setCurrentCase(casee)

					case (Some(newCase), Some((oldCase, _))) if newCase ne oldCase =>
						detachCurrent()
						setCurrentCase(newCase)

					case _ => // ok !
				}
			}

			Some((rx, obs))
		}
	}

	private def compileScopeTransformation(element: Element): Unit = {
		val locals = element.getAttribute("*scope")
		val expr = Parser.parseExpression(locals)
		element.removeAttribute("*scope")

		templateWrap(element, s"*scope $locals") { (placeholder, context, template, parent) =>
			val ctx = context.child()
			Interpreter.evaluate(expr, ctx)
			val child = template.bind(ctx)
			parent.attach(child)
			placeholder.parentNode.replaceChild(child.root, placeholder)
			None
		}
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
				registerBindingExpr(element, cond) { (elem, value, instance) =>
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
		registerBindingExpr(element, value) { (elem, value, instance) =>
			Serializer.forValue(value).write(value) match {
				case None => elem.removeAttribute(attribute)
				case Some(string) => elem.setAttribute(attribute, string)
			}
		}
	}

	/** Compiles a style binding attribute */
	private def compileStyleBindingAttribute(element: Element, target: String, value: String): Unit = {
		val style = toCamelCase(target.substring(1))
		registerBindingExpr(element, value) { (elem, value, instance) =>
			elem.dyn.style.updateDynamic(style)(value.toString)
		}
	}

	/** Compiles a class binding attribute */
	private def compileClassBindingAttribute(element: Element, target: String, value: String): Unit = {
		val className = toCamelCase(target.substring(1))
		registerBindingExpr(element, value) { (elem, value, instance) =>
			if (value.dyn) elem.classList.add(className)
			else elem.classList.remove(className)
		}
	}

	/** Compiles a property binding attribute */
	private def compilePropertyBindingAttribute(element: Element, target: String, value: String): Unit = {
		registerBindingExpr(element, value) { (elem, value, instance) =>
			elem.dyn.selectDynamic(target) match {
				case rx: Var[_] => rx.as[Var[Any]] := value
				case _ => elem.dyn.updateDynamic(target)(value.asInstanceOf[js.Any])
			}
		}
	}

	/** Compiles an event listener attribute */
	private def compileEventListenerAttribute(element: Element, name: String, value: String): Unit = {
		element.removeAttribute(name)
		val target = name.substring(1, name.length - 1)
		val expr = Parser.parseExpression(value)
		registerBinding(element) { case (elem, context, instance) =>
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
			registerBindingExpr(element, expr) { (elem, value, instance) =>
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
				registerBinding(text) { case (node, context, instance) =>
					val rx = Rx { Interpreter.safeEvaluate(expr, context) }
					val obs = Obs { node.data = rx.!.toString }
					Some((rx, obs))
				}
		}
	}

	/** Compiles a Comment node */
	private def compileComment(comment: Comment): Unit = {
		Parser.parseInterpolation(comment.data).foreach {
			case LiteralPrimitive(value) =>
				comment.data = value.toString

			case expr =>
				registerBinding(comment) { case (node, context, instance) =>
					val rx = Rx { Interpreter.safeEvaluate(expr, context) }
					val obs = Obs { node.data = rx.!.toString }
					Some((rx, obs))
				}
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

	/**
	  * An instance of the template.
	  */
	class Instance private[Template] (val context: Context) {
		/** The root node of this template */
		val root = document.importNode(template.content, true).as[DocumentFragment]

		/** Attached template instances */
		private[this] val attached = mutable.Set.empty[Template#Instance]

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
				for (binding <- builder.apply(node, context, this)) {
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
				for (a <- attached) a.enable()
			}
			enabled = true
		}

		/**
		  * Disables template bindings.
		  * Every nodes data-bound to a reactive variable will have its observer
		  * unbound from the variable and the template will stop updating.
		  */
		def disable(): Unit = if (enabled) {
			for ((v, o) <- bindings) v ~!> o
			for (a <- attached) a.disable()
			enabled = false
		}

		/** Attaches another template instance to this one and enables it if this instance is enabled */
		def attach(instance: Template#Instance): Unit = {
			attached.add(instance)
			if (enabled) instance.enable() else instance.disable()
		}

		/** Detaches another template instance from this one and disables it, unless keepEnabled is true */
		def detach(instance: Template#Instance, keepEnabled: Boolean = false): Unit = {
			attached.remove(instance)
			if (!keepEnabled) instance.disable()
		}
	}
}

object Template {
	/** The set of attribute that cannot be data-bound */
	val attrBlacklist = Set("class", "style", "*match")

	/** Id of the last Xuen binding registered */
	private[this] var lastBindingId = 0

	/** Returns the next binding unique ID */
	private def nextBindingId: String = {
		lastBindingId += 1
		Integer.toHexString(lastBindingId)
	}

	/** The type of a binding adapter */
	private type BindingAdapter = (Element) => Node

	/** The type of a binding builder for a node of type N */
	private type BindingBuilder[N <: Node] = (N, Context, Template#Instance) => Option[(Rx[_], Obs)]

	/** The set of defined bindings */
	private val bindingDefinitions = mutable.Map.empty[String, (BindingAdapter, mutable.Set[BindingBuilder[Node]])]

	/** Removes the xuen-bindings attribute tag */
	private def elementBindingsAdapter(element: Element): Node = {
		element.removeAttribute("xuen-bindings")
		element
	}

	/** Registers a binding on an Element node */
	private def registerBinding[E <: Element](element: E)(builder: BindingBuilder[E]): Unit = {
		val bindingId = Option(element.getAttribute("xuen-bindings")).getOrElse({
			val id = nextBindingId
			element.setAttribute("xuen-bindings", id)
			bindingDefinitions.put(id, (elementBindingsAdapter, mutable.Set.empty))
			id
		})
		bindingDefinitions(bindingId)._2.add(builder.asInstanceOf[BindingBuilder[Node]])
	}

	/** Registers a binding on an Element node */
	private def registerBindingExpr[E <: Element](element: E, expr: Expression)(behavior: (E, Any, Template#Instance) => Unit): Unit = {
		registerBinding(element) { case (elem, context, instance) =>
			val rx = Rx { Interpreter.safeEvaluate(expr, context) }
			val obs = Obs { behavior(elem, rx.!, instance) }
			Some((rx, obs))
		}
	}

	/** Alias for registerBindingExpr with an expression source code */
	private def registerBindingExpr[E <: Element](element: E, exprSource: String)(behavior: (E, Any, Template#Instance) => Unit): Unit = {
		registerBindingExpr(element, Parser.parseExpression(exprSource))(behavior)
	}

	/**
	  * Registers a binding on a Text node.
	  * Unlike for elements, only a single binding can be defined on a text node.
	  */
	private def registerBinding(text: Text)(builder: BindingBuilder[Text]): Unit = {
		val id = nextBindingId
		val synthElement = text.ownerDocument.createElement("xuen-interpolation")
		synthElement.setAttribute("xuen-bindings", id)
		text.parentNode.replaceChild(synthElement, text)

		def adapter(element: Element): Node = {
			val text = element.ownerDocument.createTextNode("")
			element.parentNode.replaceChild(text, element)
			text
		}

		bindingDefinitions.put(id, (adapter, mutable.Set(builder.asInstanceOf[BindingBuilder[Node]])))
	}

	/**
	  * Registers a binding on a Comment node.
	  * Unlike for elements, only a single binding can be defined on a comment node.
	  */
	private def registerBinding(comment: Comment)(builder: BindingBuilder[Comment]): Unit = {
		val id = Template.nextBindingId
		val synthElement = comment.ownerDocument.createElement("xuen-interpolation")
		synthElement.setAttribute("xuen-bindings", id)
		comment.parentNode.replaceChild(synthElement, comment)

		def adapter(element: Element): Node = {
			val comment = element.ownerDocument.createComment("")
			element.parentNode.replaceChild(comment, element)
			comment
		}

		bindingDefinitions.put(id, (adapter, mutable.Set(builder.asInstanceOf[BindingBuilder[Node]])))
	}

	/**
	  * Wraps the given element in a <template> tag and setup appropriate bindings.
	  */
	private def templateWrap(element: Element, text: String)(builderImpl: (Node, Context, Template, Template#Instance) => Option[(Rx[_], Obs)]): Unit = {
		val id = nextBindingId
		val template = document.createElement("template").as[HTMLTemplateElement]
		template.setAttribute("xuen-bindings", id)

		element.parentNode.replaceChild(template, element)
		template.content.appendChild(element)

		val child = Template(template)

		def adapter(template: Element): Node = {
			val placeholder = document.createComment(" " + text + " ")
			template.parentNode.replaceChild(placeholder, template)
			placeholder
		}

		def builder(placeholder: Node, context: Context, instance: Template#Instance): Option[(Rx[_], Obs)] = {
			builderImpl(placeholder, context, child, instance)
		}

		bindingDefinitions.put(id, (adapter, mutable.Set(builder)))
	}
}
