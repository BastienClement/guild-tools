package controllers.webtools

import play.api.mvc._

class WebTools extends Controller {
	def main = Action {
		Redirect("/wt/profile")
	}

	def catchall(path: String) = main
}
