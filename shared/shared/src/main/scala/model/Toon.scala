package model

import _root_.data.Specializations
import scala.compat.Platform
import util.annotation.data

@data case class Toon(id: Int,
                      name: String,
                      server: String,
                      owner: Int,
                      main: Boolean,
                      active: Boolean,
                      clss: Int,
                      race: Int,
                      gender: Int,
                      level: Int,
                      achievements: Int,
                      thumbnail: String,
                      ilvl: Int,
                      specid: Int,
                      invalid: Boolean = false,
                      last_update: Long = Platform.currentTime) {
	@inline def crossrealm = server match {
		case "sargeras" | "garona" | "nerzhul" => false
		case _ => true
	}

	lazy val spec = Specializations.get(specid)
	lazy val role = spec.role
}
