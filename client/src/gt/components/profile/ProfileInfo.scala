package gt.components.profile

import gt.App
import gt.components.GtHandler
import gt.components.widget.GtBox
import gt.components.widget.form.GtButton
import gt.services.{ProfileService, RosterService}
import models.{Profile, ProfileData}
import org.scalajs.dom.{console, window}
import rx.Var
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.{Failure, Success}
import utils.jsannotation.js
import xuen.Component
import xuen.compat.Platform

object ProfileInfo extends Component[ProfileInfo](
	selector = "profile-info",
	templateUrl = "/assets/imports/views/profile.html",
	dependencies = Seq(GtBox, GtButton)
)

@js class ProfileInfo extends GtHandler {
	val roster = service(RosterService)
	val profile = service(ProfileService)

	val user = property[Int]
	val editable = property[Boolean]

	val info = user ~! roster.user
	val rawData = Var[Profile](null)
	val data = rawData ~ { rd => Option(rd).map(_.withPlaceholders).getOrElse(ProfileData()) }

	val age = rawData ~ { rd =>
		Option(rd).flatMap(_.birthday).map { dt =>
			val moment = js.Dynamic.global.moment
			val now = moment()
			val birth = moment(dt.toISOString)
			now.diff(birth, "years").applyDynamic("toString")().asInstanceOf[String]
		}.getOrElse("—")
	}

	user ~> { id =>
		if (id > 0) {
			profile.userProfile(id).onComplete {
				case Success(d) => if (d.user == user.!) rawData := d
				case Failure(e) => console.error("Failed to load profile date\n\n" + App.formatException(e))
			}
		}
	}

	/** Navigates to the profile management page */
	def edit(): Unit = {
		val base = if (Platform.dev) "/auth" else "//auth.fromscratch.gg"
		window.location.href = s"$base/user/${ user.! }"
	}
}
