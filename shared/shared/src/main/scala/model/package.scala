import boopickle.DefaultBasic._
import model.calendar._

package object model {
	implicit val NewsFeedDataPickler = PicklerGenerator.generatePickler[NewsFeedData]
	implicit val ProfilePickler = PicklerGenerator.generatePickler[Profile]
	implicit val ToonPickler = PicklerGenerator.generatePickler[Toon]
	implicit val UserPickler = PicklerGenerator.generatePickler[User]

	implicit val AnswerPickler = PicklerGenerator.generatePickler[Answer]
	implicit val EventPickler = PicklerGenerator.generatePickler[Event]
	implicit val SlackPickler = PicklerGenerator.generatePickler[Slack]
	implicit val SlotPickler = PicklerGenerator.generatePickler[Slot]
	implicit val TabPickler = PicklerGenerator.generatePickler[Tab]
	implicit val EventFullPickler = PicklerGenerator.generatePickler[EventFull]

	implicit val ToonOrdering = new Ordering[Toon] {
		def compare(a: Toon, b: Toon): Int = {
			if (a.main != b.main) b.main compare a.main
			else if (a.active != b.active) b.active compare a.active
			else if (a.ilvl != b.ilvl) b.ilvl compare a.ilvl
			else a.name compare b.name
		}
	}

	implicit val EventOrdering = new Ordering[Event] {
		def compare(x: Event, y: Event): Int = {
			if (x.date != y.date) x.date compare y.date
			else if (x.isAnnounce != y.isAnnounce) y.isAnnounce compare x.isAnnounce
			else if (x.sortingTime != y.sortingTime) x.sortingTime compare y.sortingTime
			else x.id compare y.id
		}
	}
}
