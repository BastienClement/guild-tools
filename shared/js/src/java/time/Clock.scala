package java.time

abstract class Clock {
	def getZone: ZoneId
	def instant(): Instant
	def withZone(zone: ZoneId): Clock
}

object Clock {
	def systemUTC(): Clock = new Clock {
		def getZone: ZoneId = ???
		def instant(): Instant = Instant.now()
		def withZone(zone: ZoneId): Clock = ???
	}
}
