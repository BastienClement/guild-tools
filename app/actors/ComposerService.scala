package actors

import models._
import models.simple._
import reactive.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.CacheCell
import utils.CacheCell.Implicit.extractor

trait ComposerService {
	def load: (List[ComposerLockout], List[ComposerGroup], List[ComposerSlot])

	def createLockout(title: String): Unit
	def renameLockout(id: Int, title: String): Unit
	def deleteLockout(id: Int): Unit

	def createGroup(lockout: Int): Unit
	def renameGroup(id: Int, title: String): Unit
	def deleteGroup(id: Int): Unit

	def setSlot(group: Int, char: Int, role: String): Unit
	def unsetSlot(group: Int, char: Int): Unit

	def exportGroups(groups: List[Int], events: Set[Int], mode: Int, locked: Boolean): Future[Unit]
}

class ComposerServiceImpl extends ComposerService {
	val composer_lockouts = CacheCell(1.minute) {
		DB.withSession { implicit s => ComposerLockouts.list }
	}

	val composer_groups = CacheCell(1.minute) {
		DB.withSession { implicit s => ComposerGroups.list }
	}

	val composer_slots = CacheCell(1.minute) {
		DB.withSession { implicit s => ComposerSlots.list }
	}

	def load: (List[ComposerLockout], List[ComposerGroup], List[ComposerSlot]) = {
		(composer_lockouts, composer_groups, composer_slots)
	}

	def createLockout(title: String): Unit = DB.withSession { implicit s =>
		val template = ComposerLockout(0, title)
		val id = (ComposerLockouts returning ComposerLockouts.map(_.id)).insert(template)
		val lockout = template.copy(id = id)

		composer_lockouts := (lockout :: _)
		//Dispatcher !# ComposerLockoutCreate(lockout)
	}

	def renameLockout(id: Int, title: String): Unit = {
		DB.withSession { implicit s =>
			val lockout = ComposerLockouts.filter(_.id === id)
			lockout.map(_.title).update(title)
			//Dispatcher !# ComposerLockoutUpdate(lockout.first)
		}
	}

	def deleteLockout(id: Int): Unit = DB.withSession { implicit s =>
		if (ComposerLockouts.filter(_.id === id).delete > 0) {
			composer_lockouts := (_ filter (_.id != id))

			val (deleted, kept) = composer_groups.partition(_.lockout == id)
			composer_groups := kept

			val deleted_groups = deleted.map(_.id).toSet
			composer_slots := (_ filter (s => deleted_groups.contains(s.group)))

			//Dispatcher !# ComposerLockoutDelete(id)
		}
	}

	def createGroup(lockout: Int): Unit = {
		// Ensure we do not create more than 8 groups per lockout
		val gcount = composer_groups.count(_.lockout == lockout)
		if (gcount >= 8) return

		DB.withSession { implicit s =>
			val template = ComposerGroup(0, lockout, s"Group ${gcount + 1}")
			val id = (ComposerGroups returning ComposerGroups.map(_.id)).insert(template)
			val group = template.copy(id = id)

			composer_groups := (group :: _)
			//Dispatcher !# ComposerGroupCreate(group)
		}
	}

	def renameGroup(id: Int, title: String): Unit = {
		DB.withSession { implicit s =>
			val group = ComposerGroups.filter(_.id === id)
			group.map(_.title).update(title)
			//Dispatcher !# ComposerGroupUpdate(group.first)
		}
	}

	def deleteGroup(id: Int): Unit = DB.withSession { implicit s =>
		if (ComposerGroups.filter(_.id === id).delete > 0) {
			composer_groups := (_ filter (_.id != id))
			composer_slots := (_ filter (_.group != id))
			//Dispatcher !# ComposerGroupDelete(id)
		}
	}

	def setSlot(group: Int, char: Int, role: String): Unit = DB.withSession { implicit s =>
		val slot = ComposerSlot(group, char, role)
		ComposerSlots.insertOrUpdate(slot)

		composer_slots := (_ filter (s => s.group != group || s.char != char))
		composer_slots := (slot :: _)

		//Dispatcher !# ComposerSlotSet(slot)
	}

	def unsetSlot(group: Int, char: Int): Unit = DB.withSession { implicit s =>
		if (ComposerSlots.filter(s => s.group === group && s.char === char).delete > 0) {
			composer_slots := (_ filter (s => s.group != group || s.char != char))
			//Dispatcher !# ComposerSlotUnset(group, char)
		}
	}

	private def sortChars(a: Char, b: Char) = {
		if (a.role != b.role) {
			if (a.role == "TANK")
				true
			else if (a.role == "DPS" && b.role == "HEALING")
				true
			else
				false
		} else {
			a.clazz < b.clazz
		}
	}

	def exportGroups(groups: List[Int], events: Set[Int], mode: Int, locked: Boolean): Future[Unit] = Future {
		/*DB.withTransaction { implicit t =>
			val groups_and_slots = for (group <- groups) yield {
				// Fetch every slots for this group
				val slots_data = ComposerSlots.filter(_.group === group).list

				// Ensure we do not try to export a group of more than 30 players
				if (slots_data.size > 30) {
					throw new Exception("You cannot export a group with more than 30 players in it")
				}

				// Fetch and sort char informations
				val slots_chars = slots_data.map {
					s => RosterService.char(s.char).map(_.copy(role = s.role))
				} collect {
					case Some(char) => char
				} sortWith {
					sortChars(_, _)
				}

				// Fetch group meta-informations
				val group_data = ComposerGroups.filter(_.id === group).first

				(group_data, slots_chars)
			}

			val event_tabs = CalendarTabs.filter(_.event inSet events).list.groupBy(_.event)

			for ((event, tabs) <- event_tabs) {
				def insertInTab(tab: CalendarTab, chars: List[Char]) = {
					// Ensure tab is in the correct locking state before inserting
					if (tab.locked != locked) {
						CalendarService.setTabHidden(tab.id, locked)
					}

					val slots = for ((char, idx) <- chars.zipWithIndex)
						yield CalendarSlot(tab.id, idx + 1, char.owner, char.name, char.clazz, char.role)

					CalendarService.setSlots(slots)
				}

				def createTab(title: String): Future[CalendarTab] = CalendarService.createTab(event, title)
				def insertInNewTab(title: String, chars: List[Char]): Unit = for (tab <- createTab(title)) insertInTab(tab, chars)

				mode match {
					// Merge
					case 0 =>
						for ((group, chars) <- groups_and_slots) {
							val tab = tabs.find(_.title == group.title).map(Future.successful(_)) getOrElse createTab(group.title)
							for (t <- tab) {
								CalendarService.wipeTab(t.id)
								insertInTab(t, chars)
							}
						}

					// Replace
					case 1 =>
						for (tab <- tabs) CalendarService.deleteTab(tab.id)
						for ((group, chars) <- groups_and_slots) insertInNewTab(group.title, chars)

					// Append
					case 2 =>
						for ((group, chars) <- groups_and_slots) insertInNewTab(group.title, chars)
				}

				CalendarService.optimizeEvent(event)
			}
		}*/
	}
}
