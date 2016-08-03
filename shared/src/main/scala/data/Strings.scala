package data

object Strings {
	def className(id: Int): String = id match {
		case 1 => "Warrior"
		case 2 => "Paladin"
		case 3 => "Hunter"
		case 4 => "Rogue"
		case 5 => "Priest"
		case 6 => "Death Knight"
		case 7 => "Shaman"
		case 8 => "Mage"
		case 9 => "Warlock"
		case 10 => "Monk"
		case 11 => "Druid"
		case 12 => "Demon Hunter"
		case _ => "Unknown"
	}

	def raceName(id: Int): String = id match {
		case 1 => "Human"
		case 2 => "Orc"
		case 3 => "Dwarf"
		case 4 => "Night Elf"
		case 5 => "Undead"
		case 6 => "Tauren"
		case 7 => "Gnome"
		case 8 => "Troll"
		case 9 => "Goblin"
		case 10 => "Blood Elf"
		case 11 => "Draenei"
		case 22 => "Worgen"
		case 24 | 25 | 26 => "Pandaren"
		case _ => "Unknown"
	}

	def rankName(id: Int): String = id match {
		case 10 => "Guest"
		case 12 => "Casual"
		case 8 => "Apply"
		case 9 => "Member"
		case 11 => "Officer"
		case 13 => "Veteran"
		case _ => "Unknown"
	}
}
