package xuen

import org.scalajs.dom._
import org.scalajs.dom.raw.{CustomEvent, HTMLElement}
import scala.collection.mutable
import scala.language.{dynamics, existentials}
import scala.scalajs.js
import util.implicits._
import util.jsannotation.js
import util.{Microtask, Serializer, Zero}
import xuen.rx.{Obs, Rx, Var}

/**
  * The handler object associated with a custom element.
  */
@js abstract class Handler extends ComponentInstance {
	/**
	  * Called when the element is considered ready.
	  *
	  * This is done after the child dom is fully build, but before the
	  * attached callback fires for the first time.
	  */
	def ready(): Unit = {}

	/**
	  * Called when the element is attached to the DOM tree.
	  */
	def attached(): Unit = {}

	/**
	  * Called when the element is detached from the DOM tree.
	  */
	def detached(): Unit = {}

	/**
	  * Called when an attribute of the element has changed.
	  * This will not happen before the ready() event triggers.
	  *
	  * @param attr  the attribute name
	  * @param old   the old attribute value
	  * @param value the new attribute value
	  */
	def attributeChanged(attr: String, old: String, value: String): Unit = {}

	// Status flags
	private[this] var hasAttributeProxies = false
	private[this] var hasAttributeBindings = false

	/** Attributes awaiting automatic name resolving */
	private[this] lazy val attributeProxies = {
		hasAttributeProxies = true
		mutable.Map[Var[_], (String) => Unit]()
	}

	/** Attributes bound to Rx variables */
	private[this] lazy val attributeBindings = {
		hasAttributeBindings = true
		mutable.Map[String, (String) => Unit]()
	}

	/** Pending bindings to execute when ready */
	private[this] var onReadyCallbacks = js.Array[() => Unit]()

	/** This element template instance */
	private[this] var template: Template#Instance = null

	/** Performs attribute binding construction */
	private[this] def bindAttribute[T](name: String, proxy: Var[T])(implicit serializer: Serializer[T]) = {
		// Read default value
		val defaultValue = proxy.!

		// Read initial value
		val initialValue = Option(getAttribute(name))
		if (initialValue.isDefined) {
			for (value <- serializer.read(initialValue)) proxy := value
		}

		proxy ~>> { value =>
			serializer.write(value) match {
				case None if hasAttribute(name) => removeAttribute(name)
				case Some(serialized) if getAttribute(name) != serialized => setAttribute(name, serialized)
				case _ => // attribute is already in-sync
			}
		}

		attributeBindings.put(name, newValue => {
			if (newValue == null) proxy := defaultValue
			else proxy := serializer.read(Option(newValue))
		})
	}

	/** Declares an automatic attribute binding */
	protected final def attribute[T: Zero : Serializer]: Var[T] = {
		val proxy = Var(implicitly[Zero[T]].zero)
		attributeProxies.put(proxy, attr => bindAttribute(attr, proxy))
		proxy
	}

	/** Declares a named attribute binding */
	protected final def attributeNamed[T: Zero : Serializer](name: String): Var[T] = {
		val proxy = Var(implicitly[Zero[T]].zero)
		attributeProxies.put(proxy, attr => bindAttribute(name, proxy))
		proxy
	}

	/** Declares a property binding */
	protected final def property[T: Zero]: Var[T] = Var(implicitly[Zero[T]].zero)

	/** Declares an model property binding */
	protected final def model[T: Zero]: Var[T] = {
		val proxy = Var(implicitly[Zero[T]].zero)
		attributeProxies.put(proxy, attr => {
			val eventName = s"${ attr }change".toLowerCase
			proxy ~> { v => fire(eventName) }
		})
		proxy
	}

	/** Indicates if the ready hook was already called once */
	private[this] var readyCalled = false

	/** Handles the component creation */
	protected[xuen] final def createdCallback(): Unit = {
		// Create this element shadow root
		if (component.template.isDefined) this.createShadowRoot()

		// Invoke the component constructor
		Handler.construct(this, component.constructorTag.constructor)

		// Stamp the template on this element
		for (tmpl <- component.template) template = tmpl.stamp(this)

		// Setup attributes proxies
		if (hasAttributeProxies) Rx.atomically {
			for (name <- js.Object.getOwnPropertyNames(this)) this.dyn.selectDynamic(name) match {
				case rx: Var[_] if attributeProxies.contains(rx) => attributeProxies(rx)(name)
				case _ => /* ignore */
			}
			attributeProxies.clear()
		}

		// Call ready callback on next tick
		Microtask.schedule { readyCallback() }
	}

	/** Call the ready hook and set the corresponding flag */
	private[this] def readyCallback(): Unit = if (!readyCalled) {
		readyCalled = true
		onReadyCallbacks.foreach(l => l())
		onReadyCallbacks = null
		ready()
	}

	/** Handles the component attachement */
	protected[xuen] final def attachedCallback(): Unit = {
		if (!readyCalled) readyCallback()
		if (template != null) template.enable()
		attached()
	}

	/** Handles the component detachement */
	protected[xuen] final def detachedCallback(): Unit = {
		detached()
		if (template != null) template.disable()
	}

	/** Handles the change of component attribute */
	protected[xuen] final def attributeChangedCallback(attr: String, old: String, value: String): Unit = {
		for (updater <- attributeBindings.get(attr)) updater(value)
		attributeChanged(attr, old, value)
	}

	/** Queries a single child element matching the given selector */
	protected final def child = new Dynamic {
		@inline private def exec[T <: HTMLElement](selector: String): T = shadow.querySelector(selector).asInstanceOf[T]

		def selectDynamic(id: String): HTMLElement = exec[HTMLElement](s"#$id")
		def apply(selector: String): HTMLElement = exec[HTMLElement](selector)

		def as[T <: HTMLElement] = new Dynamic {
			def selectDynamic(id: String): T = exec[T](s"#$id")
			def apply(selector: String): T = exec[T](selector)
		}
	}

	/** Declares a new event listener on this element */
	protected final def listen[E <: Event](event: String)
	                                      (handler: E => Unit): Unit = listen(event, this, false)(handler)

	/** Declares a new event listener on this element */
	protected final def listen[E <: Event](event: String, capture: Boolean)
	                                      (handler: E => Unit): Unit = listen(event, this, capture)(handler)

	/** Declares a new event listener on this element */
	protected final def listen[E <: Event](event: String, target: => EventTarget)
	                                      (handler: E => Unit): Unit = listen(event, target, false)(handler)

	/** Declares a new event listener on this element */
	protected final def listen[E <: Event](event: String, target: => EventTarget, capture: Boolean)
	                                      (handler: E => Unit): Unit = {
		if (readyCalled) target.addEventListener(event, handler, capture)
		else onReadyCallbacks.push(() => listen(event, target, capture)(handler))
	}

	/** Declares a new event listener on this element */
	protected final def listenCustom[T](event: String)
	                                   (handler: T => Unit): Unit = listenCustom(event, this, false)(handler)

	/** Declares a new event listener on this element */
	protected final def listenCustom[T](event: String, capture: Boolean)
	                                   (handler: T => Unit): Unit = listenCustom(event, this, capture)(handler)

	/** Declares a new event listener on this element */
	protected final def listenCustom[T](event: String, target: => EventTarget)
	                                   (handler: T => Unit): Unit = listenCustom(event, target, false)(handler)

	/** Declares a new event listener on this element */
	protected final def listenCustom[T](event: String, target: => EventTarget, capture: Boolean)
	                                   (handler: T => Unit): Unit = {
		if (readyCalled) target.addEventListener(event, { e: CustomEvent => handler(e.detail.asInstanceOf[T]) }, capture)
		else onReadyCallbacks.push(() => listenCustom(event, target, capture)(handler))
	}

	/** Dispatches a custom event from this element */
	protected final def fire(event: String, detail: Any = null,
	                         bubbles: Boolean = true, cancelable: Boolean = true, scoped: Boolean = false): Boolean = {
		dispatchEvent(js.Dynamic.newInstance(js.Dynamic.global.CustomEvent)(event, js.Dynamic.literal(
			detail = detail.dyn,
			bubbles = bubbles,
			cancelable = cancelable,
			scoped = scoped
		)).asInstanceOf[CustomEvent])
	}

	/**
	  * Selects a child element in this component Shadow DOM matching the given selector.
	  * This method should not be called by user code and is only used by the Xuen expression context.
	  */
	protected[xuen] final def $xuen$selectElement(selector: String): HTMLElement = child(selector)
}

object Handler {
	case class VarTemplate[T](default: T) extends Var[T](default) {
		def throws = throw XuenException("Binding to Var template is forbidden")

		override def ~> (observer: Obs): this.type = throws
		override def ~>> (observer: Obs): this.type = throws
		override def <~ (rx: Rx[T]): this.type = throws

		def instance = Var[T](this: T)
	}

	/** The original HTMLElement constructor */
	private[this] val originalHTMLElement = js.Dynamic.global.HTMLElement

	/** A fake constructor to use in place of HTMLElement during handler construction */
	private[this] val dummyConstructor: js.Function = () =>  {}

	/**
	  * Constructs an handler by calling its constructor.
	  *
	  * A hack is required here since inheritance will make Scala.js call
	  * the HTMLElement constructor, which throws an IllegalConstructor
	  * exception.
	  *
	  * The true HTMLElement constructor is temporarily replaced by a dummy
	  * one that does nothing. The original one is restored once the handler
	  * constructor returns.
	  *
	  * @param instance    the handler object to constructs (this)
	  * @param constructor the constructor to invoke
	  */
	private def construct(instance: ComponentInstance, constructor: js.Dynamic): Unit = {
		js.Dynamic.global.HTMLElement = dummyConstructor
		constructor.call(instance)
		js.Dynamic.global.HTMLElement = originalHTMLElement
	}
}
