package controllers

import com.google.inject.Inject
import gt.GuildTools
import models._
import play.api.cache.Cached
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{Action, Controller, RequestHeader}
import scala.util.Try
import utils.DateTime
import utils.SlickAPI._

class APIController @Inject() (val cached: Cached) extends Controller {
	def catchall(path: String) = Action {
		NotFound(Json.obj("error" -> "Undefined API call"))
	}

	def bugsack = Action(parse.json) { request =>
		val report = request.body

		val user = (report \ "user").asOpt[Int] getOrElse 0
		val rev = (report \ "rev").as[String]
		val error = (report \ "msg").as[String]
		val stack = (report \ "stack").asOpt[String] getOrElse ""
		val navigator = (report \ "nav").asOpt[String] getOrElse ""

		val key = (user, error, stack)
		val bug = BugReport(utils.md5(key.toString()), user, DateTime.now, rev, error, stack, navigator)

		Try {
			DB.run { BugSack += bug }
		}

		NoContent
	}

	def socketURL = cached((_: RequestHeader) => "api/socket_url", 3600) {
		Action {
			Ok(GuildTools.conf.getString("socket.url").getOrElse(""))
		}
	}
}
