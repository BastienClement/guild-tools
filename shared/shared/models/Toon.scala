package models

import boopickle.DefaultBasic._
import data.Spec
import scala.compat.Platform
import utils.annotation.data

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

	lazy val spec = Spec.get(specid)
	lazy val role = spec.role
}

object Toon {
	implicit val ToonPickler = PicklerGenerator.generatePickler[Toon]

	implicit val ToonOrdering = new Ordering[Toon] {
		def compare(a: Toon, b: Toon): Int = {
			if (a.main != b.main) b.main compare a.main
			else if (a.active != b.active) b.active compare a.active
			else if (a.ilvl != b.ilvl) b.ilvl compare a.ilvl
			else a.name compare b.name
		}
	}
}
