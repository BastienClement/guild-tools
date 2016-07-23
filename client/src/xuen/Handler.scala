package xuen

import org.scalajs.dom.raw.HTMLElement
import scala.collection.mutable
import scala.scalajs.js
import util.implicits._
import util.jsannotation.js
import util.{Serializer, Zero}
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

	/** This element template instance */
	private[this] var template: Template#Instance = _

	/** Performs attribute binding construction */
	private[this] def bindAttribute[T](name: String, proxy: Var[T])(implicit serializer: Serializer[T]) = {
		// Read default value
		val defaultValue: T = proxy

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
	protected[xuen] final def attribute[T: Zero : Serializer]: Var[T] = {
		val proxy = Var(implicitly[Zero[T]].zero)
		attributeProxies.put(proxy, attr => bindAttribute(attr, proxy))
		proxy
	}

	/** Declares a named attribute binding */
	protected[xuen] final def attributeNamed[T: Zero : Serializer](name: String): Var[T] = {
		val proxy = Var(implicitly[Zero[T]].zero)
		attributeProxies.put(proxy, attr => bindAttribute(name, proxy))
		proxy
	}

	/** Declares a property binding */
	protected[xuen] final def property[T: Zero]: Var[T] = Var(implicitly[Zero[T]].zero)

	/** Handles the component creation */
	protected[xuen] final def createdCallback(): Unit = {
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

		ready()
	}

	/** Handles the component attachement */
	protected[xuen] final def attachedCallback(): Unit = {
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

	/** Select a child element in this component Shadow DOM with the requested ID */
	protected[xuen] final def $xuen$selectElement(selector: String): HTMLElement = {
		shadow.querySelector(selector).asInstanceOf[HTMLElement]
	}
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
	private[this] val dummyConstructor: js.Function = () => {}

	/**
	  * Constructs an handler by calling its constructor.
	  *
	  * A hack is required here since inheritance will make Scala.js call
	  * the HTMLElement constructor, which throws an IllegalConstructor
	  * exception.
	  *
	  * The true HTMLConstructor is temporarily replaced by a dummy one
	  * that does nothing. The original one is restored once the handler
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
