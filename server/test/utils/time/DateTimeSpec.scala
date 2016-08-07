package utils.time

import org.scalatestplus.play.PlaySpec
import util.DateTime

class DateTimeSpec extends PlaySpec {

	"DateTime.now" should {
		"returns current time" in {
			println(DateTime.now)
		}
	}
}
