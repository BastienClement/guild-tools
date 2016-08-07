package data

object UserGroups {
	/** The set of every user having developer rights */
	val developers = Set(1647)

	/** The set of every groups considered officer */
	val officers = Set(11)

	/** The set of every groups considered guild members */
	val members = Set(9, 11)

	/** The set of every groups forming the guild roster */
	val roster = Set(8, 9, 11)

	/** The set of every groups considered part of the guild */
	val fromscratch = Set(
		8, // Apply
		12, // Casual
		9, // Member
		11, // Officer
		10, // Guest
		13 // Veteran
	)
}
