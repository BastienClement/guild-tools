package models

import _root_.data.{Class, Role, Spec}
import boopickle.DefaultBasic._
import scala.compat.Platform
import utils.annotation.data

@data case class Toon(id: Int,
                      name: String,
                      server: String,
                      owner: Int,
                      main: Boolean,
                      active: Boolean,
                      classid: Int,
                      race: Int,
                      gender: Int,
                      level: Int,
                      achievements: Int,
                      thumbnail: String,
                      ilvl: Int,
                      specid: Int,
                      invalid: Boolean = false,
                      last_update: Long = Platform.currentTime) {
	/** Indicates if the toon is from another realm */
	lazy val crossrealm = server match {
		case "sargeras" | "garona" | "nerzhul" => false
		case _ => true
	}

	/** The Class data for this toon */
	lazy val clss: Class = Class.fromId(classid)

	/** The Spec data for the main specialization of this toon */
	lazy val spec: Spec = clss.specs.find(s => s.id == specid).getOrElse(Spec.Dummy)

	/** The defined role for this toon's main specialization */
	lazy val role: Role = spec.role
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

	val Dummy = Toon(0, "Unknown", "sargeras", 0, false, true, 0, 1, 0, 0, 0, "", 0, 0, true, 0)
}
