package utils

import java.sql.Timestamp
import java.util.Date
import scala.language.implicitConversions
import java.text.SimpleDateFormat
import java.util.GregorianCalendar

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
}

class SmartTimestamp(val time: Long) extends Timestamp(time) {
	def ==(that: SmartTimestamp): Boolean = time == that.time
	def !=(that: SmartTimestamp): Boolean = time != that.time
	def >(that: SmartTimestamp): Boolean = time > that.time
	def <(that: SmartTimestamp): Boolean = time < that.time
	def >=(that: SmartTimestamp): Boolean = time >= that.time
	def <=(that: SmartTimestamp): Boolean = time <= that.time

	def between(a: SmartTimestamp, b: SmartTimestamp): Boolean = this >= a && this <= b

	override def toString: String = SmartTimestamp.format.format(this)
}
