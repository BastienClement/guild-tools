package utils

import java.sql.Timestamp
import java.util.Date
import scala.language.implicitConversions
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Calendar

object SmartTimestamp {
	val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

	object Implicits {
		implicit def timestamp2smart(t: Timestamp) = new SmartTimestamp(t.getTime)
	}

	def now: SmartTimestamp = new SmartTimestamp(new Date().getTime)

	def create(year: Int, month: Int, day: Int): SmartTimestamp = {
		val calendar = new GregorianCalendar()
		calendar.set(year, month, day)
		new SmartTimestamp(calendar.getTime.getTime)
	}

	def createSQL(y: Int, m: Int, d: Int): Timestamp = create(y, m, d)

	def parse(str: String, frmt: SimpleDateFormat = format): SmartTimestamp = {
		new SmartTimestamp(frmt.parse(str).getTime)
	}
}

class SmartTimestamp(val time: Long) extends Timestamp(time) {
	lazy val calendar = {
		val c = new GregorianCalendar()
		c.setTimeInMillis(time)
		c
	}

	def ==(that: SmartTimestamp): Boolean = time == that.time
	def !=(that: SmartTimestamp): Boolean = time != that.time
	def >(that: SmartTimestamp): Boolean = time > that.time
	def <(that: SmartTimestamp): Boolean = time < that.time
	def >=(that: SmartTimestamp): Boolean = time >= that.time
	def <=(that: SmartTimestamp): Boolean = time <= that.time

	def +(that: SmartTimestamp): SmartTimestamp = new SmartTimestamp(time + that.time)
	def -(that: SmartTimestamp): SmartTimestamp = new SmartTimestamp(time - that.time)

	def between(a: SmartTimestamp, b: SmartTimestamp): Boolean = this >= a && this <= b

	def year = calendar.get(Calendar.YEAR)
	def month = calendar.get(Calendar.MONTH)
	def day = calendar.get(Calendar.DATE)

	override def toString: String = SmartTimestamp.format.format(this)
}
