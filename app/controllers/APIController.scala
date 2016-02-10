package controllers

import scala.util.Try
import models._
import models.mysql._
import play.api.Play.current
import play.api.cache.Cached
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{Action, Controller, RequestHeader}
import utils.SmartTimestamp

class APIController extends Controller {
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
		val bug = BugReport(utils.md5(key.toString()), user, SmartTimestamp.now, rev, error, stack, navigator)

		Try {
			DB.run { BugSack += bug }
		}

		NoContent
	}

	def socketURL = Cached((_: RequestHeader) => "api/socket_url", 3600) {
		Action {
			Ok(current.configuration.getString("socket.url") getOrElse "")
		}
	}
}
