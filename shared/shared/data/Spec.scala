package data

import data.Role._
import scala.collection.mutable
import utils.annotation.data

@data
sealed abstract class Spec(val id: Int, val clss: Class, val name: String, val role: Role, val icon: String) {
	def this(id: Int, name: String, role: Role, icon: String)(implicit clss: Class) = this(id, clss, name, role, icon)
}

object Spec {
	private val cache = mutable.Map[Int, Spec]()

	def get(id: Int) = cache.getOrElseUpdate(id, Class.list.map(_.specs.find(s => s.id == id)).find {
		case Some(_) => true
		case _ => false
	}.flatten.getOrElse(Dummy))

	def resolve(name: String, clss: Int) = Class.byId(clss).specs.collect { case s if s.name == name => s.id }.headOption.getOrElse(0)

	def forClass(clss: Int) = Class.byId(clss).specs

	object Dummy extends Spec(0, Class.Unknown, "Unknown", Role.Unknown, "")

	// Warrior
	object Warrior {
		implicit val clss = Class.Warrior
		object Arms extends Spec(1, "Arms", DPS, "ability_warrior_savageblow")
		object Fury extends Spec(2, "Fury", DPS, "ability_warrior_innerrage")
		object Protection extends Spec(3, "Protection", Tank, "ability_warrior_defensivestance")
	}

	// Paladin
	object Paladin {
		implicit val clss = Class.Paladin
		object Holy extends Spec(4, "Holy", Healing, "spell_holy_holybolt")
		object Protection extends Spec(5, "Protection", Tank, "ability_paladin_shieldofthetemplar")
		object Retribution extends Spec(6, "Retribution", DPS, "spell_holy_auraoflight")
	}

	// Hunter
	object Hunter {
		implicit val clss = Class.Hunter
		object BeastMastery extends Spec(7, "Beast Mastery", DPS, "ability_hunter_bestialdiscipline")
		object Marksmanship extends Spec(8, "Marksmanship", DPS, "ability_hunter_focusedaim")
		object Survival extends Spec(9, "Survival", DPS, "ability_hunter_camouflage")
	}

	// Rogue
	object Rogue {
		implicit val clss = Class.Rogue
		object Assassination extends Spec(10, "Assassination", DPS, "ability_rogue_deadlybrew")
		object Outlaw extends Spec(11, "Outlaw", DPS, "inv_sword_30")
		object Subtlety extends Spec(12, "Subtlety", DPS, "ability_stealth")
	}

	// Priest
	object Priest {
		implicit val clss = Class.Priest
		object Discipline extends Spec(13, "Discipline", Healing, "spell_holy_powerwordshield")
		object Holy extends Spec(14, "Holy", Healing, "spell_holy_guardianspirit")
		object Shadow extends Spec(15, "Shadow", DPS, "spell_shadow_shadowwordpain")
	}

	// Death Knight
	object DeathKnight {
		implicit val clss = Class.DeathKnight
		object Blood extends Spec(16, "Blood", Tank, "spell_deathknight_bloodpresence")
		object Frost extends Spec(17, "Frost", DPS, "spell_deathknight_frostpresence")
		object Unholy extends Spec(18, "Unholy", DPS, "spell_deathknight_unholypresence")
	}

	// Shaman
	object Shaman {
		implicit val clss = Class.Shaman
		object Elemental extends Spec(19, "Elemental", DPS, "spell_nature_lightning")
		object Enhancement extends Spec(20, "Enhancement", DPS, "spell_shaman_improvedstormstrike")
		object Restoration extends Spec(21, "Restoration", Healing, "spell_nature_magicimmunity")
	}

	// Mage
	object Mage {
		implicit val clss = Class.Mage
		object Arcane extends Spec(22, "Arcane", DPS, "spell_holy_magicalsentry")
		object Fire extends Spec(23, "Fire", DPS, "spell_fire_firebolt02")
		object Frost extends Spec(24, "Frost", DPS, "spell_frost_frostbolt02")
	}

	// Warlock
	object Warlock {
		implicit val clss = Class.Warlock
		object Affliction extends Spec(25, "Affliction", DPS, "spell_shadow_deathcoil")
		object Demonology extends Spec(26, "Demonology", DPS, "spell_shadow_metamorphosis")
		object Destruction extends Spec(27, "Destruction", DPS, "spell_shadow_rainoffire")
	}

	// Monk
	object Monk {
		implicit val clss = Class.Monk
		object Brewmaster extends Spec(28, "Brewmaster", Tank, "spell_monk_brewmaster_spec")
		object Mistweaver extends Spec(29, "Mistweaver", Healing, "spell_monk_mistweaver_spec")
		object Windwalker extends Spec(30, "Windwalker", DPS, "spell_monk_windwalker_spec")
	}

	// Druid
	object Druid {
		implicit val clss = Class.Druid
		object Balance extends Spec(31, "Balance", DPS, "spell_nature_starfall")
		object Feral extends Spec(32, "Feral", DPS, "ability_druid_catform")
		object Guardian extends Spec(33, "Guardian", Tank, "ability_racial_bearform")
		object Restoration extends Spec(34, "Restoration", Healing, "spell_nature_healingtouch")
	}

	// Demon Hunter
	object DemonHunter {
		implicit val clss = Class.DemonHunter
		object Havoc extends Spec(35, "Havoc", DPS, "ability_demonhunter_specdps")
		object Vengeance extends Spec(36, "Vengeance", Tank, "ability_demonhunter_spectank")
	}
}
