package java.time.format

abstract class DateTimeFormatter

object DateTimeFormatter {
	def ofPattern(str: String): DateTimeFormatter = {
		require(str == "yyyy-MM-dd'T'HH:mm:ss.SSSX", "Only ISO format is supported")
		null
	}
}
