package controllers.authentication

import play.api.mvc.{Action, Controller}

class AuthController extends Controller {
	def main() = Action {
		Ok(views.html.auth.main.render())
	}
}
