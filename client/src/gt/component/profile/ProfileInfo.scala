package gt.component.profile

import gt.App
import gt.component.GtHandler
import gt.component.widget.GtBox
import gt.component.widget.form.GtButton
import gt.service.{ProfileService, RosterService}
import model.{Profile, ProfileData}
import org.scalajs.dom.{console, window}
import rx.Var
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.{Failure, Success}
import util.jsannotation.js
import xuen.Component

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
		val base = if (app.dev) "/auth" else "//auth.fromscratch.gg"
		window.location.href = s"$base/user/${ user.! }"
	}
}
