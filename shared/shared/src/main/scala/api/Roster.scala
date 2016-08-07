package api

import boopickle.DefaultBasic._
import model.{Toon, User}

object Roster {
	case class UserData(user: User, main: Option[Int], toons: Seq[Toon])

	implicit val UserTemplatePickler = PicklerGenerator.generatePickler[UserData]
}
