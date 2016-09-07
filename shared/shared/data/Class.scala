package data

import utils.annotation.data

@data
sealed abstract class Class(val id: Int, val name: String) {
	val specs: Seq[Spec]
}

object Class {
	val list = Seq(
		Warrior, Paladin, Hunter, Rogue, Priest, DeathKnight,
		Shaman, Mage, Warlock, Monk, Druid, DemonHunter)

	private val index = list.map(c => (c.id, c)).toMap
	def fromId(id: Int): Class = index.getOrElse(id, Unknown)

	object Unknown extends Class(0, "Unknown") {
		val specs = Seq(Spec.Dummy)
	}

	object Warrior extends Class(1, "Warrior") {
		val specs = Seq(
			Spec.Warrior.Arms,
			Spec.Warrior.Fury,
			Spec.Warrior.Protection)
	}

	object Paladin extends Class(2, "Paladin") {
		val specs = Seq(
			Spec.Paladin.Holy,
			Spec.Paladin.Protection,
			Spec.Paladin.Retribution)
	}

	object Hunter extends Class(3, "Hunter") {
		val specs = Seq(
			Spec.Hunter.BeastMastery,
			Spec.Hunter.Marksmanship,
			Spec.Hunter.Survival)
	}

	object Rogue extends Class(4, "Rogue") {
		val specs = Seq(
			Spec.Rogue.Assassination,
			Spec.Rogue.Outlaw,
			Spec.Rogue.Subtlety)
	}

	object Priest extends Class(5, "Priest") {
		val specs = Seq(
			Spec.Priest.Discipline,
			Spec.Priest.Holy,
			Spec.Priest.Shadow)
	}

	object DeathKnight extends Class(6, "Death Knight") {
		val specs = Seq(
			Spec.DeathKnight.Blood,
			Spec.DeathKnight.Frost,
			Spec.DeathKnight.Unholy)
	}

	object Shaman extends Class(7, "Shaman") {
		val specs = Seq(
			Spec.Shaman.Elemental,
			Spec.Shaman.Enhancement,
			Spec.Shaman.Restoration)
	}

	object Mage extends Class(8, "Mage") {
		val specs = Seq(
			Spec.Mage.Arcane,
			Spec.Mage.Fire,
			Spec.Mage.Frost)
	}

	object Warlock extends Class(9, "Warlock") {
		val specs = Seq(
			Spec.Warlock.Affliction,
			Spec.Warlock.Demonology,
			Spec.Warlock.Destruction)
	}

	object Monk extends Class(10, "Monk") {
		val specs = Seq(
			Spec.Monk.Brewmaster,
			Spec.Monk.Mistweaver,
			Spec.Monk.Windwalker)
	}

	object Druid extends Class(11, "Druid") {
		val specs = Seq(
			Spec.Druid.Balance,
			Spec.Druid.Feral,
			Spec.Druid.Guardian,
			Spec.Druid.Restoration)
	}

	object DemonHunter extends Class(12, "Demon Hunter") {
		val specs = Seq(
			Spec.DemonHunter.Havoc,
			Spec.DemonHunter.Vengeance)
	}
}
