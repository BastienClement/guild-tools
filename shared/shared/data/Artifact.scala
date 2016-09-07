package data

import utils.annotation.data
import _root_.data.Relic._

@data
sealed abstract class Artifact(id: Int, name: String, relics: (Relic, Relic, Relic))

object Artifact {
	object Atiesh extends Artifact(22589, "Atiesh, Greatstaff of the Guardian", (Arcane, Shadow, Fel))

	object Stromkar extends Artifact(128910, "Strom'kar, the Warbreaker", (Iron, Blood, Shadow))
	object WarswordsOfTheValarjar extends Artifact(128908, "Warswords of the Valarjar", (Fire, Storm, Iron))
	object ScaleOfTheEarthWarder extends Artifact(128289, "Scale of the Earth-Warder", (Iron, Blood, Fire))

	object TheSilverHand extends Artifact(128823, "The Silver Hand", (Holy, Life, Holy))
	object Truthguard extends Artifact(128866, "Truthguard", (Holy, Iron, Arcane))
	object Ashbringer extends Artifact(120978, "Ashbringer", (Holy, Fire, Holy))

	object Titanstrike extends Artifact(128861, "Titanstrike", (Storm, Arcane, Iron))
	object Thasdorah extends Artifact(0, "Thas'dorah, Legacy of the Windrunner", (Storm, Blood, Life))
	object Talonclaw extends Artifact(0, "Talonclaw, Spear of the Wild Gods", (Storm, Iron, Blood))

	object TheKingslayers extends Artifact(0, "The Kingslayers", (Shadow, Iron, Blood))
	object TheDreadblades extends Artifact(0, "The Dreadblades", (Blood, Iron, Storm))
	object FangsOfTheDevourer extends Artifact(0, "Fangs of the Devourer", (Fel, Shadow, Fel))

	object LightsWrath extends Artifact(0, "Light's Wrath", (Holy, Shadow, Holy))
	object Tuure extends Artifact(0, "T'uure, Beacon of the Naaru", (Holy, Life, Holy))
	object Xalatath extends Artifact(0, "Xal'atath, Blade of the Black Empire", (Shadow, Blood, Shadow))

	object MawOfTheDamned extends Artifact(0, "Maw of the Damned", (Blood, Shadow, Iron))
	object BladesOfTheFallenPrince extends Artifact(0, "Blades of the Fallen Prince", (Frost, Shadow, Frost))
	object Apocalypse extends Artifact(0, "Apocalypse", (Fire, Shadow, Blood))

	object TheFistOfRaden extends Artifact(0, "The Fist of Ra-den", (Storm, Frost, Storm))
	object Doomhammer extends Artifact(0, "Doomhammer", (Fire, Iron, Storm))
	object Sharasdal extends Artifact(0, "Sharas'dal, Scepter of Tides", (Life, Frost, Life))

	object Aluneth extends Artifact(0, "Aluneth, Greatstaff of the Magna", (Arcane, Frost, Arcane))
	object Felomelorn extends Artifact(0, "Felo'melorn", (Fire, Arcane, Fire))
	object Ebonchill extends Artifact(0, "Ebonchill, Greatstaff of Alodi", (Frost, Arcane, Frost))

	object Ulthalesh extends Artifact(0, "Ulthalesh, the Deadwind Harvester", (Shadow, Blood, Shadow))
	object SkullOfTheManari extends Artifact(0, "Skull of the Man'ari", (Shadow, Fire, Fel))
	object ScepterOfSargeras extends Artifact(0, "Scepter of Sargeras", (Life, Fire, Life))

	object FuZan extends Artifact(0, "Fu Zan, The Wanderer's Companion", (Life, Storm, Iron))
	object Sheilun extends Artifact(0, "Sheilun, Staff of the Mists", (Frost, Life, Storm))
	object FistsOfTheHeavens extends Artifact(0, "Fists of the Heavens", (Storm, Iron, Storm))

	object ScytheOfElune extends Artifact(0, "Scythe of Elune", (Arcane, Life, Arcane))
	object FangsOfAshamane extends Artifact(0, "Fangs of Ashamane", (Frost, Blood, Life))
	object ClawsOfUrsoc extends Artifact(0, "Claws of Ursoc", (Fire, Blood, Life))
	object Ghanir extends Artifact(0, "G'Hanir, the Mother Tree", (Life, Frost, Life))

	object TwinbladesOfTheDeceiver extends Artifact(0, "Twinblades of the Deceiver", (Life, Storm, Life))
	object TheAldrachiWarblades extends Artifact(0, "The Aldrachi Warblades", (Iron, Arcane, Life))
}
