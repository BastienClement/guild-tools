package xuen

import facade.HTMLTemplateElement
import facade.ShadowDOM._
import org.scalajs.dom._
import scala.collection.mutable
import scala.scalajs.js
import util.Implicits._
import util.Serializer
import xuen.expr.{Interpreter, Parser}
import xuen.rx.syntax.ExplicitExtractor
import xuen.rx.{Obs, Rx}

/**
  * A component template, defined by a <xuen-component> tag.
  *
  * @param element the <xuen-component> element
  */
case class Template(element: Element) {
	/** The selector of the associated component */
	val selector = element.getAttribute("id")
	if (selector == null) throw XuenException("Unable to determine template selector")

	/** The template element in the <xuen-component> */
	val template = element.querySelector(s"xuen-component[id='$selector'] > template").as[HTMLTemplateElement]
	if (template == null) throw XuenException(s"No <template> element found in <xuen-component id='$selector'>")

	type BindingBuilder[N <: Node] = (N, Context) => Option[(Rx[_], Obs)]
	type BindingAdapter = (Element) => Node

	private[this] val bindingDefinitions = mutable.Map.empty[String, (BindingAdapter, mutable.Set[BindingBuilder[Node]])]

	template.content.normalize()
	compile(template.content)

	/** Removes the xuen-bindings attribute tag */
	def elementBindingsAdapter(element: Element): Node = {
		element.removeAttribute("xuen-bindings")
		element
	}

	/** Registers a binding on an Element node */
	def registerBinding[E <: Element](element: E)(builder: BindingBuilder[E]): Unit = {
		val bindingId = Option(element.getAttribute("xuen-bindings")).getOrElse({
			val id = Template.nextBindingId
			element.setAttribute("xuen-bindings", id)
			bindingDefinitions.put(id, (elementBindingsAdapter, mutable.Set.empty))
			id
		})
		bindingDefinitions(bindingId)._2.add(builder.asInstanceOf[BindingBuilder[Node]])
	}

	/** Reverts the <xuen-interpolation> placeholder to a text node */
	def textBindingsAdapter(element: Element): Node = {
		val text = element.ownerDocument.createTextNode("")
		element.parentNode.replaceChild(text, element)
		text
	}

	/**
	  * Registers a binding on a Text node.
	  * Unlike for elements, only a single binding can be defined on a text node.
	  */
	def registerBinding(text: Text)(builder: BindingBuilder[Text]): Unit = {
		val id = Template.nextBindingId
		val synthElement = text.ownerDocument.createElement("xuen-interpolation")
		synthElement.setAttribute("xuen-bindings", id)
		text.parentNode.replaceChild(synthElement, text)
		bindingDefinitions.put(id, (textBindingsAdapter, mutable.Set(builder.asInstanceOf[BindingBuilder[Node]])))
	}

	/** Compiles a sub-tree of the template */
	//noinspection UnitMethodIsParameterless
	def compile(node: Node): Unit = {
		// Common operations aliases
		@inline def recurse = node.childNodes.foreach(compile)

		node match {
			case element: Element =>
				if (element.hasAttributes) {
					val attrs = for (attr <- element.attributes) yield attr.asInstanceOf[Attr]
					for {
						attr <- attrs
						name = attr.name
						if !Template.attrBlacklist.contains(name)
					} {
						val last = name.last
						name.head match {
							case '#' =>
								element.removeAttribute(name)
								element.setAttribute("id", name.substring(1))

							case '.' =>
								element.removeAttribute(name)
								element.classList.add(name.substring(1))

							case '[' if last == ']' =>
								val target = name.substring(1, name.length - 1)
								val expr = Parser.parseExpression(attr.value)
								element.removeAttribute(name)
								if (target.head == '@') {
									// Attribute binding
									val attribute = target.substring(1)
									registerBinding(element) { case (elem, context) =>
										val rx = Rx { Interpreter.safeEvaluate(expr, context) }
										val obs = Obs {
											val value = rx.!
											Serializer.forValue(value).write(value) match {
												case None => elem.removeAttribute(attribute)
												case Some(string) => elem.setAttribute(attribute, string)
											}
										}
										Some((rx, obs))
									}
								} else {
									// Property binding
									registerBinding(element) { case (elem, context) =>
										val rx = Rx { Interpreter.safeEvaluate(expr, context) }
										val obs = Obs { elem.dyn.updateDynamic(target)(rx.!.asInstanceOf[js.Any]) }
										Some((rx, obs))
									}
								}

							case '(' if last == ')' =>
								val target = name.substring(1, name.length - 1)
								val expr = Parser.parseExpression(attr.value)
								element.removeAttribute(name)
								registerBinding(element) { case (elem, context) =>
									val dispatchContext = context.child()
									elem.addEventListener(target, (event: Event) => {
										dispatchContext.set("$event", event)
										Interpreter.safeEvaluate(expr, dispatchContext)
									})
									None
								}

							case _ =>
								for (expr <- Parser.parseInterpolation(attr.value)) {
									element.removeAttribute(name)
									registerBinding(element) { case (elem, context) =>
										val rx = Rx { Interpreter.safeEvaluate(expr, context) }
										val obs = Obs {
											val value = rx.!
											Serializer.forValue(value).write(value) match {
												case None => elem.removeAttribute(name)
												case Some(string) => elem.setAttribute(name, string)
											}
										}
										Some((rx, obs))
									}
								}
						}
					}
				}
				recurse

			case text: Text =>
				for (expr <- Parser.parseInterpolation(text.data)) {
					registerBinding(text) { case (elem, context) =>
						val rx = Rx { Interpreter.safeEvaluate(expr, context) }
						val obs = Obs { elem.data = rx.!.toString }
						Some((rx, obs))
					}
				}

			case fragment: DocumentFragment => recurse
			case comment: Comment => // Ignore
			case unknown => throw XuenException(s"Encountered unknown node type '$unknown' while compiling template")
		}
	}

	/**
	  * An instance of the template.
	  *
	  * @param component the corresponding component handler
	  */
	case class Instance(component: ComponentInstance) {
		/** The root node of this template */
		val root = template.content.cloneNode(true).as[DocumentFragment]

		/** The root context */
		implicit val context = Context.ref(component)

		/** The list of data-bindings defined for this template */
		private[this] val bindings = mutable.Set.empty[(Rx[_], Obs)]

		/** The current state of the template bindings */
		private[this] var enabled = false

		init()
		component.createShadowRoot().appendChild(root)

		/** Initialize the template instance */
		private def init(): Unit = {
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

	/** Stamps this template on the given handler element */
	def stamp(component: ComponentInstance): Instance = Instance(component)
}

object Template {
	/** Id of the last Xuen binding registered */
	private[this] var lastBindingId = 0

	private[Template] def nextBindingId: String = {
		lastBindingId += 1
		lastBindingId.toString
	}

	val attrBlacklist = Set("class")
}
