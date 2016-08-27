package utils

import boopickle.DefaultBasic._
import java.sql.Timestamp
import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.{ChronoUnit, TemporalAmount}
import java.util.concurrent.TimeUnit
import java.util.{Calendar, GregorianCalendar}
import platform.DateTimeCompat
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
	implicit def fromLocalDate(ld: LocalDate): DateTime = fromLocalDateTime(LocalDateTime.of(ld, LocalTime.ofSecondOfDay(0)))

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

	/** Constructs a new DateTime from the given milliseconds timestamp */
	def apply(millis: Long): DateTime = new DateTime(Instant.ofEpochMilli(millis))

	/** Constructs a DateTime holding the current time */
	def now: DateTime = fromInstant(clock.instant())

	def zero: DateTime = apply(0)

	/** Constructs a DateTime holding the current day at 00:00:00 */
	def today: DateTime = fromInstant(clock.instant().truncatedTo(ChronoUnit.DAYS))

	// Pickler
	implicit val DateTimePickler = transformPickler[DateTime, Long](apply)(_.timestamp)

	// Duration helpers
	implicit final class Units(val amount: Int) extends AnyVal {
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
class DateTime private (val instant: Instant) extends Ordered[DateTime] {
	val date = implicitly[DateTimeCompat].instantAtOffset(instant, DateTime.utc)
	@inline def timestamp = instant.toEpochMilli

	def year = date.getYear
	def month = date.getMonthValue
	def day = date.getDayOfMonth
	def hour = date.getHour
	def minute = date.getMinute
	def second = date.getSecond
	def nano = date.getNano

	// Comparators
	override def equals(obj: Any): Boolean = obj match {
		case null => false
		case dt: DateTime => timestamp == dt.timestamp
		case _ => false
	}

	override def hashCode(): Int = timestamp.hashCode()

	def compare(that: DateTime): Int = (timestamp, that.timestamp) match {
		case (a, b) if a < b => -1
		case (a, b) if a > b => 1
		case _ => 0
	}

	def between(a: DateTime, b: DateTime) = a <= this && this <= b

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

	// Calendar key format
	def toCalendarKey: Int = ((year - 2000) * 12 + (month - 1)) * 32 + day

	// SQL Timestamp201
	lazy val toTimestamp = {
		val cal = new GregorianCalendar
		cal.set(year, month - 1, day, hour, minute, second)
		cal.set(Calendar.MILLISECOND, 0)
		new Timestamp(cal.getTimeInMillis)
	}
}
