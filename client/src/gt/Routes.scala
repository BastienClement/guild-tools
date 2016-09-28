package gt

import gt.components.View
import gt.components.calendar.GtCalendar
import gt.components.calendar.event.GtCalendarEvent
import gt.components.composer.GtComposer
import gt.components.dashboard.GtDashboard
import gt.components.misc.{GtAbout, GtSettings}
import gt.components.profile.GtProfile
import gt.components.streams.GtStreams
import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.JSStringOps._
import scala.scalajs.js.RegExp

object Routes {
	final val definitions = js.Array[RouteDefinition](
		"/dashboard" -> GtDashboard,

		"/profile(/:[0-9]+:user)?" -> GtProfile,

		"/calendar" -> GtCalendar,
		"/calendar/event/:[0-9]+:eventid" -> GtCalendarEvent,

		"/composer(/:[0-9]+:docid)?" -> GtComposer,

		"/streams" -> GtStreams,

		"/about" -> GtAbout,
		"/settings" -> GtSettings

		/*

	"/apply(/:[0-9]+:selected)?" -> null,
	"/apply-guest" -> null,

	"/streams" -> null,
	"/streams/settings" -> null,
	"/streams/whitelist" -> null,

	"/roster" -> null,

	"/server-status" -> null,
	*/
	)

	case class RouteDefinition(pattern: String, view: View) {
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
					val value = matches(i + 1).asInstanceOf[js.UndefOr[String]].orNull
					if (value != null) args.push((tags(i), value))
					i += 1
				}
				Some(args)
			} else None
		}
	}

	private[this] implicit class PatternDefinition(private val pattern: String) extends AnyVal {
		def -> (view: View): RouteDefinition = RouteDefinition(pattern, view)
	}
}
