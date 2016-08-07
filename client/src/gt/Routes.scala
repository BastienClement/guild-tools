package gt

import gt.component.View
import gt.component.dashboard.GtDashboard
import gt.component.misc.GtAbout
import gt.component.profile.GtProfile
import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.JSStringOps._
import scala.scalajs.js.RegExp

object Routes {
	final val definitions = js.Array[RouteDefinition](
		"/dashboard" -> GtDashboard,

		"/profile(/:[0-9]+:user)?" -> GtProfile,

		"/about" -> GtAbout

		/*
	"/profile(/:[0-9]+:user)?" -> null,

	"/calendar" -> null,

	"/apply(/:[0-9]+:selected)?" -> null,
	"/apply-guest" -> null,

	"/streams" -> null,
	"/streams/settings" -> null,
	"/streams/whitelist" -> null,

	"/roster" -> null,

	"/about" -> null,
	"/server-status" -> null,
	"/settings" -> null
	*/
	)

	case class RouteDefinition(pattern: String, view: View = null) {
		def -> (view: View) = copy(view = view)

		lazy val (regexp, tags) = {
			// Remove trailing slashes and make parens non-capturing
			// to prevent breaking tags capture
			val path = pattern.jsReplace(RegExp("/$"), "").jsReplace(RegExp("\\(", "g"), "(?:")

			// Extract tag names
			val tags = Option(path.`match`(RegExp("[^?]:(?:[^:]+:)?([a-z_0-9\\-]+)", "g"))).getOrElse(js.Array())
			val tagsName = tags.map { tag => tag.jsSlice(tag.lastIndexOf(":") + 1) }

			val res = path.jsReplace(RegExp("([^?]):(?:([^:]+):)?[a-z_0-9\\-]+", "g"), (all: Any, prefix: String, pattern: String) => {
				s"$prefix(${ Option(pattern).getOrElse("[^/]+") })"
			})

			(RegExp(s"^$res$$"), tagsName)
		}

		def matches(path: String): Option[Seq[(String, String)]] = {
			val matches = path.`match`(regexp)
			if (matches != null) {
				val args = js.Array[(String, String)]()
				var i = 0
				while (i < tags.length) {
					args.push((tags(i), matches(i + 1).asInstanceOf[js.UndefOr[String]].orNull))
					i += 1
				}
				Some(args)
			} else None
		}
	}

	private[this] implicit def StringToDefinition(str: String): RouteDefinition = RouteDefinition(str)
}
