package data

import utils.annotation.data

object Specializations {
	// Roles aliases
	private final val DPS = "DPS"
	private final val HEALING = "HEALING"
	private final val TANK = "TANK"

	/** Specializations data */
	private var specs = Vector.empty[Spec]

	@data case class Spec(clss: Int, name: String, role: String, icon: String) {
		val id = specs.size
		specs = specs :+ this
	}

	val Dummy = Spec(0, "Unknown", "UNKNOW", "")

	// Warrior
	val Arms = Spec(1, "Arms", DPS, "ability_warrior_savageblow")
	val Fury = Spec(1, "Fury", DPS, "ability_warrior_innerrage")
	val ProtectionWarrior = Spec(1, "Protection", TANK, "ability_warrior_defensivestance")

	// Paladin
	val HolyPaladin = Spec(2, "Holy", HEALING, "spell_holy_holybolt")
	val ProtectionPaladin = Spec(2, "Protection", TANK, "ability_paladin_shieldofthetemplar")
	val Retribution = Spec(2, "Retribution", DPS, "spell_holy_auraoflight")

	// Hunter
	val BeastMastery = Spec(3, "Beast Mastery", DPS, "ability_hunter_bestialdiscipline")
	val Marksmanship = Spec(3, "Marksmanship", DPS, "ability_hunter_focusedaim")
	val Survival = Spec(3, "Survival", DPS, "ability_hunter_camouflage")

	// Rogue
	val Assassination = Spec(4, "Assassination", DPS, "ability_rogue_deadlybrew")
	val Outlaw = Spec(4, "Outlaw", DPS, "inv_sword_30")
	val Subtlety = Spec(4, "Subtlety", DPS, "ability_stealth")

	// Priest
	val Discipline = Spec(5, "Discipline", HEALING, "spell_holy_powerwordshield")
	val HolyPriest = Spec(5, "Holy", HEALING, "spell_holy_guardianspirit")
	val Shadow = Spec(5, "Shadow", DPS, "spell_shadow_shadowwordpain")

	// Death Knight
	val Blood = Spec(6, "Blood", TANK, "spell_deathknight_bloodpresence")
	val FrostDk = Spec(6, "Frost", DPS, "spell_deathknight_frostpresence")
	val Unholy = Spec(6, "Unholy", DPS, "spell_deathknight_unholypresence")

	// Shaman
	val Elemental = Spec(7, "Elemental", DPS, "spell_nature_lightning")
	val Enhancement = Spec(7, "Enhancement", DPS, "spell_shaman_improvedstormstrike")
	val RestorationShaman = Spec(7, "Restoration", HEALING, "spell_nature_magicimmunity")

	// Mage
	val Arcane = Spec(8, "Arcane", DPS, "spell_holy_magicalsentry")
	val Fire = Spec(8, "Fire", DPS, "spell_fire_firebolt02")
	val FrostMage = Spec(8, "Frost", DPS, "spell_frost_frostbolt02")

	// Warlock
	val Affliction = Spec(9, "Affliction", DPS, "spell_shadow_deathcoil")
	val Demonology = Spec(9, "Demonology", DPS, "spell_shadow_metamorphosis")
	val Destruction = Spec(9, "Destruction", DPS, "spell_shadow_rainoffire")

	// Monk
	val Brewmaster = Spec(10, "Brewmaster", TANK, "spell_monk_brewmaster_spec")
	val Mistweaver = Spec(10, "Mistweaver", HEALING, "spell_monk_mistweaver_spec")
	val Windwalker = Spec(10, "Windwalker", DPS, "spell_monk_windwalker_spec")

	// Druid
	val Balance = Spec(11, "Balance", DPS, "spell_nature_starfall")
	val Feral = Spec(11, "Feral", DPS, "ability_druid_catform")
	val Guardian = Spec(11, "Guardian", TANK, "ability_racial_bearform")
	val RestorationDruid = Spec(11, "Restoration", HEALING, "spell_nature_healingtouch")

	// Demon Hunter
	val Havoc = Spec(12, "Havoc", DPS, "ability_demonhunter_specdps")
	val Vengeance = Spec(12, "Vengeance", TANK, "ability_demonhunter_spectank")

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
