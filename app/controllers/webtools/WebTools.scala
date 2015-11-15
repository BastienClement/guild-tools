package controllers.webtools

import play.api.mvc._

class WebTools extends Controller with WtController {
	def main = UserAction { req =>
		Redirect("/wt/profile")
	}

	def catchall(path: String) = main
}
