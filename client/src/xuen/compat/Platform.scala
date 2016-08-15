package xuen.compat

import org.scalajs.dom
import scala.language.implicitConversions
import scala.scalajs.js

object Platform {
	private val window = dom.window.asInstanceOf[js.Dynamic]

	@inline private implicit final def DynCastBoolean(dyn: js.Dynamic): Boolean = {
		dyn.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)
	}

	/** The application is running as a standalone app */
	final val standalone: Boolean = window.APP

	/** The application is running in dev mode */
	final val dev: Boolean = window.DEV

	/** The application is running in prod mode */
	final val prod: Boolean = !dev

	/** The application is running on an unsupported platform */
	final val compat: Boolean = window.COMPAT

	/** The application is running on Firefox */
	final val isFirefox: Boolean = window.bowser.firefox

	/** The application is running on Edge */
	final val isEdge: Boolean = window.bowser.msedge
}
