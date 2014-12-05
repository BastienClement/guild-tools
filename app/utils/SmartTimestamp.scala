package utils

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.{Calendar, Date, GregorianCalendar}
import scala.compat.Platform
import scala.language.implicitConversions

/**
 * Time manipulation object
 */
object SmartTimestamp {
	/**
	 * Display format
	 */
	val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

	/**
	 * Convert a SQL timestamp to a SmartTimestamp
	 */
	implicit def fromTimestamp(t: Timestamp): SmartTimestamp = SmartTimestamp(t.getTime)

	/**
	 * Create a SmartTimestamp for the current time
	 */
	def now: SmartTimestamp = SmartTimestamp(Platform.currentTime)

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
	def +(that: SmartTimestamp): SmartTimestamp = SmartTimestamp(time + that.time)
	def -(that: SmartTimestamp): SmartTimestamp = SmartTimestamp(time - that.time)

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
	val asSQL: Timestamp = this

	/**
	 * Override the default toString implementation, use format in companion object
	 */
	override def toString: String = SmartTimestamp.format.format(this)
}
