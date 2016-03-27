package utils

import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.{ChronoUnit, TemporalAmount}
import java.util.concurrent.TimeUnit
import models.mysql._
import play.api.libs.json.{Format, JsSuccess, JsValue, Json}
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

object DateTime {
	val clock = Clock.systemUTC()

	val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
	val sqlDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
	val sqlDateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S")

	val utc = ZoneOffset.UTC

	/** Converts a Instant to a DateTime */
	implicit def from(instant: Instant): DateTime = new DateTime(instant.truncatedTo(ChronoUnit.MILLIS))

	/** Converts a OffsetDateTime to a DateTime */
	implicit def from(odt: OffsetDateTime): DateTime = from(odt.toInstant)

	/** Converts a LocalDateTime to a DateTime */
	implicit def from(ldt: LocalDateTime): DateTime = from(ldt.atOffset(utc))

	/** Converts a LocalDate to a DateTime */
	implicit def from(ld: LocalDate): DateTime = from(ld.atStartOfDay())

	/** Parses ISO date time string */
	def parse(iso: String): DateTime = from(OffsetDateTime.parse(iso, isoFormat))
	def parseSQL(sql: String): DateTime = {
		sql.length match {
			case 10 => from(LocalDate.parse(sql, sqlDateFormat))
			case 21 => from(LocalDateTime.parse(sql, sqlDateTimeFormat))
			case len => throw new IllegalArgumentException(s"Invalid length $len ($sql)")
		}
	}

	/** Constructs a DateTime at the given year-month-day date */
	def apply(year: Int, month: Int, day: Int): DateTime = from(LocalDate.of(year, month, day))

	/** Constructs a DateTime at the given year-month-day HH:mm:ss time */
	def apply(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) = {
		from(LocalDateTime.of(year, month, day, hour, minute, second))
	}

	/** Constructs a DateTime at the given year-month-day HH:mm:ss.SSS time */
	def apply(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, millis: Int) = {
		from(LocalDateTime.of(year, month, day, hour, minute, second)) + millis.millis
	}

	/** Constructs a DateTime holding the current time */
	def now: DateTime = from(clock.instant())

	/** Constructs a DateTime holding the current day at 00:00:00 */
	def today: DateTime = from(clock.instant().truncatedTo(ChronoUnit.DAYS))

	// Json format
	implicit val DateTimeFormat = new Format[DateTime] {
		def reads(json: JsValue) = JsSuccess(DateTime.parse(json.as[String]))
		def writes(dt: DateTime) = Json.obj("$date" -> dt.toISOString)
	}

	// Slick definitions
	implicit def fromTimestamp(ts: String): DateTime = DateTime.parseSQL(ts)
	implicit def toTimestamp(dt: DateTime): String = dt.toSQLString
	implicit val DateTimeColumnType = MappedColumnType.base[DateTime, String](toTimestamp, fromTimestamp)

	// Duration helper
	implicit class Units(val amount: Int) extends AnyVal {
		@inline def millis = Duration.of(amount, ChronoUnit.MILLIS)
		@inline def seconds = Duration.of(amount, ChronoUnit.SECONDS)
		@inline def minutes = Duration.of(amount, ChronoUnit.MINUTES)
		@inline def hours = Duration.of(amount, ChronoUnit.HOURS)

		@inline def milli = millis
		@inline def second = seconds
		@inline def minute = minutes
		@inline def hour = hours

		@inline def days = Period.ofDays(amount)
		@inline def weeks = Period.ofWeeks(amount)
		@inline def months = Period.ofMonths(amount)
		@inline def years = Period.ofYears(amount)

		@inline def day = days
		@inline def week = weeks
		@inline def month = months
		@inline def year = years
	}
}

/**
  * An UTC DateTime instant with milliseconds precision
  */
class DateTime private (val instant: Instant) {
	val date = instant.atOffset(DateTime.utc)
	def timestamp = instant.toEpochMilli

	// Comparators
	def == (that: DateTime): Boolean = instant.compareTo(that.instant) == 0
	def != (that: DateTime): Boolean = instant.compareTo(that.instant) != 0
	def > (that: DateTime): Boolean = instant.compareTo(that.instant) > 0
	def < (that: DateTime): Boolean = instant.compareTo(that.instant) < 0
	def >= (that: DateTime): Boolean = instant.compareTo(that.instant) >= 0
	def <= (that: DateTime): Boolean = instant.compareTo(that.instant) <= 0

	def between(a: DateTime, b: DateTime) = this >= a && this <= b

	// Date arithmetic
	def + (duration: FiniteDuration): DateTime = instant.plusMillis(duration.toMillis)
	def - (duration: FiniteDuration): DateTime = instant.minusMillis(duration.toMillis)

	def + (duration: TemporalAmount): DateTime = date.plus(duration)
	def - (duration: TemporalAmount): DateTime = date.minus(duration)

	def - (that: DateTime): FiniteDuration = {
		val delta = instant.until(that.instant, ChronoUnit.MILLIS)
		FiniteDuration(delta, TimeUnit.MILLISECONDS)
	}

	// String format
	override def toString = s"DateTime($toISOString)"
	def toISOString = date.format(DateTime.isoFormat)
	def toSQLString = date.format(DateTime.sqlDateTimeFormat)
}
