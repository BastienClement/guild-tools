package actors

import scala.concurrent.duration._
import actors.Actors._
import api._
import models._
import models.mysql._
import utils.LazyCache

trait ComposerService {
	def load: (List[ComposerLockout], List[ComposerGroup], List[ComposerSlot])

	def createLockout(title: String): Unit
	def deleteLockout(id: Int): Unit

	def createGroup(lockout: Int): Unit
	def renameGroup(id: Int, title: String): Unit
	def deleteGroup(id: Int): Unit

	def setSlot(group: Int, char: Int, role: String): Unit
	def unsetSlot(group: Int, char: Int): Unit

	def exportLockout(lockout: Int, events: List[Int]): Option[String]
	def exportGroup(group: Int, events: List[Int]): Option[String]
}

class ComposerServiceImpl extends ComposerService {
	val composer_lockouts = LazyCache(1.minute) {
		DB.withSession { implicit s => ComposerLockouts.list }
	}

	val composer_groups = LazyCache(1.minute) {
		DB.withSession { implicit s => ComposerGroups.list }
	}

	val composer_slots = LazyCache(1.minute) {
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
		Dispatcher !# ComposerLockoutCreate(lockout)
	}

	def deleteLockout(id: Int): Unit = DB.withSession { implicit s =>
		if (ComposerLockouts.filter(_.id === id).delete > 0) {
			composer_lockouts := (_ filter (_.id != id))

			val (deleted, kept) = composer_groups.partition(_.lockout == id)
			composer_groups := kept

			val deleted_groups = deleted.map(_.id).toSet
			composer_slots := (_ filter (s => deleted_groups.contains(s.group)))

			Dispatcher !# ComposerLockoutDelete(id)
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
			Dispatcher !# ComposerGroupCreate(group)
		}
	}

	def renameGroup(id: Int, title: String): Unit = {
		DB.withSession { implicit s =>
			val group = ComposerGroups.filter(_.id === id)
			group.map(_.title).update(title)
			Dispatcher !# ComposerGroupUpdate(group.first)
		}
	}

	def deleteGroup(id: Int): Unit = DB.withSession { implicit s =>
		if (ComposerGroups.filter(_.id === id).delete > 0) {
			composer_groups := (_ filter (_.id != id))
			composer_slots := (_ filter (_.group != id))
			Dispatcher !# ComposerGroupDelete(id)
		}
	}

	def setSlot(group: Int, char: Int, role: String): Unit = DB.withSession { implicit s =>
		val slot = ComposerSlot(group, char, role)
		ComposerSlots.insertOrUpdate(slot)

		composer_slots := (_ filter (s => s.group != group || s.char != char))
		composer_slots := (slot :: _)

		Dispatcher !# ComposerSlotSet(slot)
	}

	def unsetSlot(group: Int, char: Int): Unit = DB.withSession { implicit s =>
		if (ComposerSlots.filter(s => s.group === group && s.char === char).delete > 0) {
			composer_slots := (_ filter (s => s.group != group || s.char != char))
			Dispatcher !# ComposerSlotUnset(group, char)
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

	def exportLockout(lockout: Int, events: List[Int]): Option[String] = {
		None
	}

	def exportGroup(group: Int, events: List[Int]): Option[String] = {
		DB.withSession { implicit s =>
			val slots = ComposerSlots.filter(_.group === group).list
			if (slots.size > 30) {
				Some("You cannot export a group with more than 30 players in it")
			} else {
				val chars = slots.map {
					s => RosterService.char(s.char).map(_.copy(role = s.role))
				} collect {
					case Some(char) => char
				} sortWith {
					sortChars(_, _)
				}

				None
			}
		}
	}
}
