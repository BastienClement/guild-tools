package controllers.webtools

import play.api.mvc.Controller
import reactive.ExecutionContext
import scala.concurrent.Future
import utils.SlickAPI._

class CalendarController extends Controller with WtController {
	def unread_events = UserAction.async { req =>
		val count =
			if (req.user.roster) {
				val query =
					sql"""
					SELECT COUNT(*)
					FROM gt_events AS e
					JOIN phpbb_users AS u ON u.user_id = ${req.user.id}
					LEFT JOIN gt_answers AS a ON e.id = a.event AND a.user = u.user_id
					WHERE e.date >= NOW() AND e.garbage = 0 AND (
						(e.type IN (1, 2, 5) AND a.answer IS NULL)
			         OR (e.type = 3 AND a.answer = 0)
			      )
				"""
				query.as[Int].head.run
			} else {
				Future.successful(0)
			}

		val callback = req.getQueryString("callback").getOrElse("jsonp")
		count.map(c => Ok(callback + "(" + c.toString + ")").as("application/javascript"))
	}
}
