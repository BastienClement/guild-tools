package data

import util.annotation.data

object Specializations {
	@data case class Spec(id: Int, clss: Int, name: String, role: String, icon: String, inactive: Boolean = false)

	// Roles aliases
	private final val DPS = "DPS"
	private final val HEALING = "HEALING"
	private final val TANK = "TANK"

	/** Constructs dummy specialization */
	private def dummy(id: Int, clss: Int = 0): Spec = Spec(id, clss, "Unknown", "UNKNOW", "", inactive = true)

	/** Specializations data */
	private val specs = IndexedSeq(
		dummy(0),

		// Warrior
		Spec(1, 1, "Arms", DPS, "ability_warrior_savageblow"),
		Spec(2, 1, "Fury", DPS, "ability_warrior_innerrage"),
		Spec(3, 1, "Protection", TANK, "ability_warrior_defensivestance"),

		// Paladin
		Spec(4, 2, "Holy", HEALING, "spell_holy_holybolt"),
		Spec(5, 2, "Protection", TANK, "ability_paladin_shieldofthetemplar"),
		Spec(6, 2, "Retribution", DPS, "spell_holy_auraoflight"),

		// Hunter
		Spec(7, 3, "Beast Mastery", DPS, "ability_hunter_bestialdiscipline"),
		Spec(8, 3, "Marksmanship", DPS, "ability_hunter_focusedaim"),
		Spec(9, 3, "Survival", DPS, "ability_hunter_camouflage"),

		// Rogue
		Spec(10, 4, "Assassination", DPS, "ability_rogue_deadlybrew"),
		Spec(11, 4, "Combat", DPS, "ability_backstab", inactive = true),
		Spec(12, 4, "Subtlety", DPS, "ability_stealth"),

		// Priest
		Spec(13, 5, "Discipline", HEALING, "spell_holy_powerwordshield"),
		Spec(14, 5, "Holy", HEALING, "spell_holy_guardianspirit"),
		Spec(15, 5, "Shadow", DPS, "spell_shadow_shadowwordpain"),

		// Death Knight
		Spec(16, 6, "Blood", TANK, "spell_deathknight_bloodpresence"),
		Spec(17, 6, "Frost", DPS, "spell_deathknight_frostpresence"),
		Spec(18, 6, "Unholy", DPS, "spell_deathknight_unholypresence"),

		// Shaman
		Spec(19, 7, "Elemental", DPS, "spell_nature_lightning"),
		Spec(20, 7, "Enhancement", DPS, "spell_shaman_improvedstormstrike"),
		Spec(21, 7, "Restoration", HEALING, "spell_nature_magicimmunity"),

		// Mage
		Spec(22, 8, "Arcane", DPS, "spell_holy_magicalsentry"),
		Spec(23, 8, "Fire", DPS, "spell_fire_firebolt02"),
		Spec(24, 8, "Frost", DPS, "spell_frost_frostbolt02"),

		// Warlock
		Spec(25, 9, "Affliction", DPS, "spell_shadow_deathcoil"),
		Spec(26, 9, "Demonology", DPS, "spell_shadow_metamorphosis"),
		Spec(27, 9, "Destruction", DPS, "spell_shadow_rainoffire"),

		// Monk
		Spec(28, 10, "Brewmaster", TANK, "spell_monk_brewmaster_spec"),
		Spec(29, 10, "Mistweaver", HEALING, "spell_monk_mistweaver_spec"),
		Spec(30, 10, "Windwalker", DPS, "spell_monk_windwalker_spec"),

		// Druid
		Spec(31, 11, "Balance", DPS, "spell_nature_starfall"),
		Spec(32, 11, "Feral", DPS, "ability_druid_catform"),
		Spec(33, 11, "Guardian", TANK, "ability_racial_bearform"),
		Spec(34, 11, "Restoration", HEALING, "spell_nature_healingtouch"),

		// Rogue v2
		Spec(35, 4, "Outlaw", DPS, "inv_sword_30"),

		// Demon Hunter
		Spec(36, 12, "Havoc", DPS, "ability_demonhunter_specdps"),
		Spec(37, 12, "Vengeance", TANK, "ability_demonhunter_spectank")
	)

	/** Specializations by class */
	private val byClass = specs.filter(!_.inactive).groupBy(s => s.clss)

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
