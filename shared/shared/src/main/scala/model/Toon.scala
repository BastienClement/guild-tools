package model

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
                      role: String,
                      invalid: Boolean = false,
                      last_update: Long = Platform.currentTime) {
	@inline def crossrealm = server match {
		case "sargeras" | "garona" | "nerzhul" => false
		case _ => true
	}
}
