package data

import utils.annotation.data

@data sealed abstract class Spec(val clss: Int, val name: String, val role: String, val icon: String) {
	val id = Spec.specs.size
	Spec.specs = Spec.specs :+ this
}

object Spec {
	// Roles aliases
	private final val DPS = "DPS"
	private final val HEALING = "HEALING"
	private final val TANK = "TANK"

	/** Specializations data */
	private var specs = Vector.empty[Spec]

	object Dummy extends Spec(0, "Unknown", "UNKNOW", "")

	// Warrior
	object Arms extends Spec(1, "Arms", DPS, "ability_warrior_savageblow")
	object Fury extends Spec(1, "Fury", DPS, "ability_warrior_innerrage")
	object ProtectionWarrior extends Spec(1, "Protection", TANK, "ability_warrior_defensivestance")

	// Paladin
	object HolyPaladin extends Spec(2, "Holy", HEALING, "spell_holy_holybolt")
	object ProtectionPaladin extends Spec(2, "Protection", TANK, "ability_paladin_shieldofthetemplar")
	object Retribution extends Spec(2, "Retribution", DPS, "spell_holy_auraoflight")

	// Hunter
	object BeastMastery extends Spec(3, "Beast Mastery", DPS, "ability_hunter_bestialdiscipline")
	object Marksmanship extends Spec(3, "Marksmanship", DPS, "ability_hunter_focusedaim")
	object Survival extends Spec(3, "Survival", DPS, "ability_hunter_camouflage")

	// Rogue
	object Assassination extends Spec(4, "Assassination", DPS, "ability_rogue_deadlybrew")
	object Outlaw extends Spec(4, "Outlaw", DPS, "inv_sword_30")
	object Subtlety extends Spec(4, "Subtlety", DPS, "ability_stealth")

	// Priest
	object Discipline extends Spec(5, "Discipline", HEALING, "spell_holy_powerwordshield")
	object HolyPriest extends Spec(5, "Holy", HEALING, "spell_holy_guardianspirit")
	object Shadow extends Spec(5, "Shadow", DPS, "spell_shadow_shadowwordpain")

	// Death Knight
	object Blood extends Spec(6, "Blood", TANK, "spell_deathknight_bloodpresence")
	object FrostDk extends Spec(6, "Frost", DPS, "spell_deathknight_frostpresence")
	object Unholy extends Spec(6, "Unholy", DPS, "spell_deathknight_unholypresence")

	// Shaman
	object Elemental extends Spec(7, "Elemental", DPS, "spell_nature_lightning")
	object Enhancement extends Spec(7, "Enhancement", DPS, "spell_shaman_improvedstormstrike")
	object RestorationShaman extends Spec(7, "Restoration", HEALING, "spell_nature_magicimmunity")

	// Mage
	object Arcane extends Spec(8, "Arcane", DPS, "spell_holy_magicalsentry")
	object Fire extends Spec(8, "Fire", DPS, "spell_fire_firebolt02")
	object FrostMage extends Spec(8, "Frost", DPS, "spell_frost_frostbolt02")

	// Warlock
	object Affliction extends Spec(9, "Affliction", DPS, "spell_shadow_deathcoil")
	object Demonology extends Spec(9, "Demonology", DPS, "spell_shadow_metamorphosis")
	object Destruction extends Spec(9, "Destruction", DPS, "spell_shadow_rainoffire")

	// Monk
	object Brewmaster extends Spec(10, "Brewmaster", TANK, "spell_monk_brewmaster_spec")
	object Mistweaver extends Spec(10, "Mistweaver", HEALING, "spell_monk_mistweaver_spec")
	object Windwalker extends Spec(10, "Windwalker", DPS, "spell_monk_windwalker_spec")

	// Druid
	object Balance extends Spec(11, "Balance", DPS, "spell_nature_starfall")
	object Feral extends Spec(11, "Feral", DPS, "ability_druid_catform")
	object Guardian extends Spec(11, "Guardian", TANK, "ability_racial_bearform")
	object RestorationDruid extends Spec(11, "Restoration", HEALING, "spell_nature_healingtouch")

	// Demon Hunter
	object Havoc extends Spec(12, "Havoc", DPS, "ability_demonhunter_specdps")
	object Vengeance extends Spec(12, "Vengeance", TANK, "ability_demonhunter_spectank")

	/** Specializations by class */
	private val byClass = specs.groupBy(s => s.clss)

	/**
	  * Returns the specialization data from a spec ID
	  * @param id the specialization ID
	  */
	def get(id: Int): Spec = specs(id)

	/**
	  * Returns the ordered list of all specializations for a given class.
	  * @param clss the class
	  */
	def forClass(clss: Int): Seq[Spec] = byClass.getOrElse(clss, Seq.empty)

	def resolve(name: String, clss: Int): Int = specs.find(s => s.clss == clss && s.name == name).map(_.id).getOrElse(0)
}
