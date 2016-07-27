package util

import org.scalajs.dom
import scala.language.implicitConversions

object Settings {
	/** Alias to local storage object */
	val storage = dom.window.localStorage

	object Setting {
		implicit def settingExtractor[T](setting: Setting[T]): T = setting.value
	}

	/** A local storage setting */
	case class Setting[T : Serializer](key: String, default: T) {
		private val serializer = implicitly[Serializer[T]]

		/** Returns this setting current value */
		def value: T = serializer.read(Option(storage.getItem(key)), default)

		/** Checks if the settings have the given value */
		def is(req: T): Boolean = value == req

		/** Checks if the settings does not have the given value */
		def isnt(value: T): Boolean = !is(value)

		/** Sets the setting to a new value */
		def := (value: T): Unit = this := Some(value)

		/** Sets or removes the setting */
		def := (value: Option[T]): Unit = value.flatMap(serializer.write) match {
			case Some(v) => storage.setItem(key, v)
			case None => storage.removeItem(key)
		}
	}

	/** Skip loading animation synchronization */
	val `loading.fast` = Setting("loading.fast", false)

	/** Sets the verbose flag on the server socket */
	val `socket.verbose` = Setting("socket.verbose", false)

	/** The session key */
	val `auth.session` = Setting("auth.session", null: String)
}
