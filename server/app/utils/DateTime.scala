package utils

import java.sql.Timestamp
import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.{ChronoUnit, TemporalAmount}
import java.util.concurrent.TimeUnit
import java.util.{Calendar, GregorianCalendar}
import models.mysql._
import play.api.libs.json.{Format, JsSuccess, JsValue, Json}
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

object DateTime {
	private val clock = Clock.systemUTC()
	private val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
	private val utc = ZoneOffset.UTC

	/** Converts a Instant to a DateTime */
	implicit def fromInstant(instant: Instant): DateTime = new DateTime(instant.truncatedTo(ChronoUnit.MILLIS))

	/** Converts a OffsetDateTime to a DateTime */
	implicit def fromOffsetDateTime(odt: OffsetDateTime): DateTime = fromInstant(odt.toInstant)

	/** Converts a LocalDateTime to a DateTime */
	implicit def fromLocalDateTime(ldt: LocalDateTime): DateTime = fromOffsetDateTime(ldt.atOffset(utc))

	/** Converts a LocalDate to a DateTime */
	implicit def fromLocalDate(ld: LocalDate): DateTime = fromLocalDateTime(ld.atStartOfDay())

	/** Converts a Timestamp to a DateTime */
	implicit def fromTimestamp(ts: Timestamp): DateTime = fromLocalDateTime(ts.toLocalDateTime)

	/** Parses ISO date time string */
	def parse(iso: String): DateTime = fromOffsetDateTime(OffsetDateTime.parse(iso, isoFormat))

	/** Constructs a DateTime at the given year-month-day date */
	def apply(year: Int, month: Int, day: Int): DateTime = fromLocalDate(LocalDate.of(year, month, day))

	/** Constructs a DateTime at the given year-month-day HH:mm:ss time */
	def apply(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) = {
		fromLocalDateTime(LocalDateTime.of(year, month, day, hour, minute, second))
	}

	/** Constructs a DateTime at the given year-month-day HH:mm:ss.SSS time */
	def apply(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, millis: Int) = {
		fromLocalDateTime(LocalDateTime.of(year, month, day, hour, minute, second)) + millis.millis
	}

	/** Constructs a DateTime holding the current time */
	def now: DateTime = fromInstant(clock.instant())

	/** Constructs a DateTime holding the current day at 00:00:00 */
	def today: DateTime = fromInstant(clock.instant().truncatedTo(ChronoUnit.DAYS))

	// Json format
	implicit val DateTimeFormat = new Format[DateTime] {
		def reads(json: JsValue) = JsSuccess(DateTime.parse(json.as[String]))
		def writes(dt: DateTime) = Json.obj("$date" -> dt.toISOString)
	}

	// Slick definitions
	implicit val DateTimeColumnType = MappedColumnType.base[DateTime, Timestamp](_.toTimestamp, fromTimestamp)

	// Duration helpers
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
  * An UTC DateTime with milliseconds precision without all the timezone bullshit.
  */
class DateTime private (val instant: Instant) {
	val date = instant.atOffset(DateTime.utc)
	def timestamp = instant.toEpochMilli

	def year = date.getYear
	def month = date.getMonthValue
	def day = date.getDayOfMonth
	def hour = date.getHour
	def minute = date.getMinute
	def second = date.getSecond
	def nano = date.getNano

	// Comparators
	def == (that: DateTime): Boolean = instant.compareTo(that.instant) == 0
	def != (that: DateTime): Boolean = instant.compareTo(that.instant) != 0
	def > (that: DateTime): Boolean = instant.compareTo(that.instant) > 0
	def < (that: DateTime): Boolean = instant.compareTo(that.instant) < 0
	def >= (that: DateTime): Boolean = instant.compareTo(that.instant) >= 0
	def <= (that: DateTime): Boolean = instant.compareTo(that.instant) <= 0

	def between(a: DateTime, b: DateTime) = this >= a && this <= b

	// Date arithmetic with Scala duration
	def + (duration: FiniteDuration): DateTime = instant.plusMillis(duration.toMillis)
	def - (duration: FiniteDuration): DateTime = instant.minusMillis(duration.toMillis)

	// Date arithmetic with Java temporal amount
	def + (duration: TemporalAmount): DateTime = date.plus(duration)
	def - (duration: TemporalAmount): DateTime = date.minus(duration)

	// Duration between two dates
	def - (that: DateTime): FiniteDuration = {
		val delta = instant.until(that.instant, ChronoUnit.MILLIS)
		FiniteDuration(delta, TimeUnit.MILLISECONDS)
	}

	// String format
	override def toString = s"DateTime($toISOString)"
	def toISOString = date.format(DateTime.isoFormat)

	// SQL Timestamp
	private lazy val toTimestamp = {
		val cal = new GregorianCalendar
		cal.set(year, month - 1, day, hour, minute, second)
		cal.set(Calendar.MILLISECOND, 0)
		new Timestamp(cal.getTimeInMillis)
	}
}
