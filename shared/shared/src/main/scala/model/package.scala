import boopickle.DefaultBasic._

package object model {
	implicit val ProfilePickler = PicklerGenerator.generatePickler[Profile]
	implicit val ToonPickler = PicklerGenerator.generatePickler[Toon]
	implicit val UserPickler = PicklerGenerator.generatePickler[User]

	implicit val ToonOrdering = new Ordering[Toon] {
		def compare(a: Toon, b: Toon): Int = {
			if (a.main != b.main) b.main compare a.main
			else if (a.active != b.active) b.active compare a.active
			else if (a.ilvl != b.ilvl) b.ilvl compare a.ilvl
			else a.name compare b.name
		}
	}
}
