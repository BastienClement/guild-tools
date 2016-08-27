package java.time

class LocalDateTime(val date: LocalDate, val time: LocalTime) {
	def toInstant(offset: ZoneOffset): Instant = {
		require(offset == null, "Only UTF offset is supported")
		val epochDay = date.toEpochDay
		val secs = epochDay * 86400 + time.toSecondOfDay
		Instant.ofEpochSecond(secs, time.getNano)
	}

	def atOffset(offset: ZoneOffset): OffsetDateTime = OffsetDateTime.of(this, null)

	def getYear: Int = date.getYear
	def getMonthValue: Int = date.getMonthValue
	def getDayOfMonth: Int = date.getDayOfMonth
	def getHour: Int = time.getHour
	def getMinute: Int = time.getMinute
	def getSecond: Int = time.getSecond
	def getNano: Int = time.getNano
}

object LocalDateTime {
	def of(date: LocalDate, time: LocalTime): LocalDateTime = new LocalDateTime(date, time)

	def of(year: Int, month: Int, day: Int, hour: Int, minute: Int, seconds: Int): LocalDateTime = {
		val date = LocalDate.of(year, month, day)
		val time = LocalTime.of(hour, minute, seconds)
		of(date, time)
	}
}
