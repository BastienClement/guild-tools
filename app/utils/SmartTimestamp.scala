package utils

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.{Calendar, GregorianCalendar, TimeZone}

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

/**
  * Time manipulation object
  */
object SmartTimestamp {
	/** System clock with UTC timezone */
	val clock = Clock.systemUTC()

	/** Default UTC TimeZone */
	val utc = TimeZone.getTimeZone("UTC")

	/**
	  * Display format
	  */
	val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
	format.setTimeZone(utc)

	/**
	  * ISO Date format
	  */
	val iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
	iso.setTimeZone(utc)

	/**
	  * Convert a SQL timestamp to a SmartTimestamp
	  */
	implicit def fromTimestamp(t: Timestamp): SmartTimestamp = SmartTimestamp(t.getTime)
	implicit def toTimestamp(s: SmartTimestamp): Timestamp = s.toSQL

	/**
	  * Return the current UTC time
	  */
	def time: Long = clock.instant().toEpochMilli

	/**
	  * Create a SmartTimestamp for the current time
	  */
	def now: SmartTimestamp = SmartTimestamp(time)

	/**
	  * Create a SmartTimestamp for the current day at 00:00:00
	  */
	def today: SmartTimestamp = {
		val n = now
		SmartTimestamp(n.year, n.month, n.day)
	}

	/**
	  * Create a SmartTimestamp for the given day and time
	  */
	def apply(year: Int, month: Int, day: Int, hours: Int = 0, mins: Int = 0, secs: Int = 0): SmartTimestamp = {
		val calendar = new GregorianCalendar()
		calendar.set(year, month, day, hours, mins, secs)
		calendar.setTimeZone(utc)
		SmartTimestamp(calendar.getTime.getTime)
	}

	/**
	  * Create a SmartTimestamp from a given timestamp in milliseconds
	  */
	def apply(time: Long) = new SmartTimestamp(time / 1000 * 1000)

	/**
	  * Create a SmartTimestamp from a string
	  */
	def parse(str: String, frmt: SimpleDateFormat = format): SmartTimestamp = SmartTimestamp(frmt.parse(str).getTime)
}

class SmartTimestamp(val time: Long) extends Timestamp(time) {
	/**
	  * Require to access calendar information about the timestamp
	  */
	lazy val calendar = {
		val c = new GregorianCalendar()
		c.setTimeZone(SmartTimestamp.utc)
		c.setTimeInMillis(time)
		c
	}

	/**
	  * Comparators
	  */
	def ==(that: SmartTimestamp): Boolean = time == that.time
	def !=(that: SmartTimestamp): Boolean = time != that.time
	def >(that: SmartTimestamp): Boolean = time > that.time
	def <(that: SmartTimestamp): Boolean = time < that.time
	def >=(that: SmartTimestamp): Boolean = time >= that.time
	def <=(that: SmartTimestamp): Boolean = time <= that.time

	/**
	  * Date arithmetic
	  */
	def +(that: FiniteDuration): SmartTimestamp = SmartTimestamp(time + that.toMillis)
	def -(that: FiniteDuration): SmartTimestamp = SmartTimestamp(time - that.toMillis)
	def -(that: SmartTimestamp): FiniteDuration = FiniteDuration(time - that.time, TimeUnit.MILLISECONDS)

	def between(a: SmartTimestamp, b: SmartTimestamp): Boolean = this >= a && this <= b

	/**
	  * Access invidual date components
	  */
	def year = calendar.get(Calendar.YEAR)
	def month = calendar.get(Calendar.MONTH)
	def day = calendar.get(Calendar.DATE)
	def hours = calendar.get(Calendar.HOUR)
	def minutes = calendar.get(Calendar.MINUTE)
	def seconds = calendar.get(Calendar.SECOND)
	def dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

	/**
	  * Force this object to have the Timestamp type
	  */
	val toSQL: Timestamp = this

	/**
	  * Override the default toString implementation, use format in companion object
	  */
	override def toString: String = SmartTimestamp.format.format(this)
}
