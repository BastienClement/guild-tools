package gt

import data.Strings
import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.JSStringOps._
import scala.scalajs.js.timers.setInterval
import xuen.expr.PipesCollection
import xuen.rx.{Rx, Var}

object Pipes extends PipesCollection {
	declare("uppercase", (_: Any) match {
		case str: String => str.toUpperCase
		case other => other
	})

	declare("lowercase", (_: Any) match {
		case str: String => str.toLowerCase
		case other => other
	})

	declare("capitalize", (_: Any) match {
		case str: String if str.length > 0 => str.head.toUpper + str.tail
		case other => other
	})

	declare("class", Strings.className _)
	declare("race", Strings.raceName _)
	declare("rank", Strings.rankName _)

	locally {
		val phoneFormats = Seq(
			"+33 x xx xx xx xx",
			"+41 xx xxx xx xx",
			"+32 xxx xx xx xx",
			"+222 xxxx xxxx"
		).map { format => (format, format.substring(0, format.indexOf(" "))) }

		def applyFormatting(format: String, prefix: String, raw: String): String = {
			var formatted = prefix
			val digits = raw.substring(prefix.length).jsSplit("")

			var i = prefix.length
			while (i < format.length) {
				val char = format.charAt(i)
				formatted += (if (char == 'x' && digits.length > 0) digits.shift() else char)
				i += 1
			}

			if (digits.length > 0) {
				formatted += " " + digits.join("")
			}

			formatted
		}

		declare("phone", (value: String) => {
			val raw = value.replaceAll("[^0-9]+", "")
			phoneFormats.find { case (_, prefix) =>
				prefix == raw.substring(0, prefix.length)
			} match {
				case Some((format, prefix)) => applyFormatting(format, prefix, raw)
				case None => value
			}
		})
	}

	locally {
		lazy val time = {
			var clock = Var(0)
			setInterval(1.minute) { clock := clock.! + 1 }
			clock
		}

		def ago(timestamp: Any): Rx[String] = Rx {
			time.!
			js.Dynamic.global.moment(timestamp.asInstanceOf[js.Dynamic]).fromNow().asInstanceOf[String]
		}

		declare("ago", (timestamp: Any) => timestamp match {
			case _: String | _: Int | _: Double => ago(timestamp)
			case long: Long => ago(long.toDouble)
			case other => other
		})
	}
}