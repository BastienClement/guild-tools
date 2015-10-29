package controllers.webtools

import controllers.WebTools
import models._
import models.mysql._
import reactive._

trait ApplyController {
	this: WebTools =>

	def applyMember = UserAction.async { req =>
		val user = req.user
		if (!user.member) throw Deny
		for (applys <- Applys.openForUser(user).result.run) yield {
			Ok(views.html.wt.applications.render(applys, user))
		}
	}
}
