package data

import data.Role._
import scala.collection.mutable
import utils.annotation.data
import _root_.data.Artifact._

@data
sealed abstract class Spec(val id: Int, val clss: Class, val name: String, val role: Role, val icon: String) {
	def this(id: Int, name: String, role: Role, icon: String)(implicit clss: Class) = this(id, clss, name, role, icon)

	val artifact: Artifact
}

object Spec {
	private val cache = mutable.Map[Int, Spec]()
	private def resolve(id: Int) = {
		Class.list.map(_.specs.find(s => s.id == id)).find {
			case Some(_) => true
			case _ => false
		}.flatten.getOrElse(Dummy)
	}

	def fromId(id: Int) = cache.getOrElseUpdate(id, resolve(id))

	object Dummy extends Spec(0, Class.Unknown, "Unknown", Role.Unknown, "") {
		val artifact = Atiesh
	}

	// Warrior
	object Warrior {
		implicit val clss = Class.Warrior

		object Arms extends Spec(1, "Arms", DPS, "ability_warrior_savageblow") {
			val artifact = Stromkar
		}

		object Fury extends Spec(2, "Fury", DPS, "ability_warrior_innerrage") {
			val artifact = WarswordsOfTheValarjar
		}

		object Protection extends Spec(3, "Protection", Tank, "ability_warrior_defensivestance") {
			val artifact = ScaleOfTheEarthWarder
		}
	}

	// Paladin
	object Paladin {
		implicit val clss = Class.Paladin

		object Holy extends Spec(4, "Holy", Healing, "spell_holy_holybolt") {
			val artifact = TheSilverHand
		}

		object Protection extends Spec(5, "Protection", Tank, "ability_paladin_shieldofthetemplar") {
			val artifact = Truthguard
		}

		object Retribution extends Spec(6, "Retribution", DPS, "spell_holy_auraoflight") {
			val artifact = Ashbringer
		}
	}

	// Hunter
	object Hunter {
		implicit val clss = Class.Hunter

		object BeastMastery extends Spec(7, "Beast Mastery", DPS, "ability_hunter_bestialdiscipline") {
			val artifact = Titanstrike
		}

		object Marksmanship extends Spec(8, "Marksmanship", DPS, "ability_hunter_focusedaim") {
			val artifact = Thasdorah
		}

		object Survival extends Spec(9, "Survival", DPS, "ability_hunter_camouflage") {
			val artifact = Talonclaw
		}
	}

	// Rogue
	object Rogue {
		implicit val clss = Class.Rogue

		object Assassination extends Spec(10, "Assassination", DPS, "ability_rogue_deadlybrew") {
			val artifact = TheKingslayers
		}

		object Outlaw extends Spec(11, "Outlaw", DPS, "inv_sword_30") {
			val artifact = TheDreadblades
		}

		object Subtlety extends Spec(12, "Subtlety", DPS, "ability_stealth") {
			val artifact = FangsOfTheDevourer
		}
	}

	// Priest
	object Priest {
		implicit val clss = Class.Priest

		object Discipline extends Spec(13, "Discipline", Healing, "spell_holy_powerwordshield") {
			val artifact = LightsWrath
		}

		object Holy extends Spec(14, "Holy", Healing, "spell_holy_guardianspirit") {
			val artifact = Tuure
		}

		object Shadow extends Spec(15, "Shadow", DPS, "spell_shadow_shadowwordpain") {
			val artifact = Xalatath
		}
	}

	// Death Knight
	object DeathKnight {
		implicit val clss = Class.DeathKnight

		object Blood extends Spec(16, "Blood", Tank, "spell_deathknight_bloodpresence") {
			val artifact = MawOfTheDamned
		}

		object Frost extends Spec(17, "Frost", DPS, "spell_deathknight_frostpresence") {
			val artifact = BladesOfTheFallenPrince
		}

		object Unholy extends Spec(18, "Unholy", DPS, "spell_deathknight_unholypresence") {
			val artifact = Apocalypse
		}
	}

	// Shaman
	object Shaman {
		implicit val clss = Class.Shaman

		object Elemental extends Spec(19, "Elemental", DPS, "spell_nature_lightning") {
			val artifact = TheFistOfRaden
		}

		object Enhancement extends Spec(20, "Enhancement", DPS, "spell_shaman_improvedstormstrike") {
			val artifact = Doomhammer
		}

		object Restoration extends Spec(21, "Restoration", Healing, "spell_nature_magicimmunity") {
			val artifact = Sharasdal
		}
	}

	// Mage
	object Mage {
		implicit val clss = Class.Mage

		object Arcane extends Spec(22, "Arcane", DPS, "spell_holy_magicalsentry") {
			val artifact = Aluneth
		}

		object Fire extends Spec(23, "Fire", DPS, "spell_fire_firebolt02") {
			val artifact = Felomelorn
		}

		object Frost extends Spec(24, "Frost", DPS, "spell_frost_frostbolt02") {
			val artifact = Ebonchill
		}
	}

	// Warlock
	object Warlock {
		implicit val clss = Class.Warlock

		object Affliction extends Spec(25, "Affliction", DPS, "spell_shadow_deathcoil") {
			val artifact = Ulthalesh
		}

		object Demonology extends Spec(26, "Demonology", DPS, "spell_shadow_metamorphosis") {
			val artifact = SkullOfTheManari
		}

		object Destruction extends Spec(27, "Destruction", DPS, "spell_shadow_rainoffire") {
			val artifact = ScepterOfSargeras
		}
	}

	// Monk
	object Monk {
		implicit val clss = Class.Monk

		object Brewmaster extends Spec(28, "Brewmaster", Tank, "spell_monk_brewmaster_spec") {
			val artifact = FuZan
		}

		object Mistweaver extends Spec(29, "Mistweaver", Healing, "spell_monk_mistweaver_spec") {
			val artifact = Sheilun
		}

		object Windwalker extends Spec(30, "Windwalker", DPS, "spell_monk_windwalker_spec") {
			val artifact = FistsOfTheHeavens
		}
	}

	// Druid
	object Druid {
		implicit val clss = Class.Druid

		object Balance extends Spec(31, "Balance", DPS, "spell_nature_starfall") {
			val artifact = ScytheOfElune
		}

		object Feral extends Spec(32, "Feral", DPS, "ability_druid_catform") {
			val artifact = FangsOfAshamane
		}

		object Guardian extends Spec(33, "Guardian", Tank, "ability_racial_bearform") {
			val artifact = ClawsOfUrsoc
		}

		object Restoration extends Spec(34, "Restoration", Healing, "spell_nature_healingtouch") {
			val artifact = Ghanir
		}
	}

	// Demon Hunter
	object DemonHunter {
		implicit val clss = Class.DemonHunter

		object Havoc extends Spec(35, "Havoc", DPS, "ability_demonhunter_specdps") {
			val artifact = TwinbladesOfTheDeceiver
		}

		object Vengeance extends Spec(36, "Vengeance", Tank, "ability_demonhunter_spectank") {
			val artifact = TheAldrachiWarblades
		}
	}
}
