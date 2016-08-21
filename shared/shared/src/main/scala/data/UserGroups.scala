package data

object UserGroups {
	final val Officer = 11
	final val Member = 9
	final val Apply = 8
	final val Casual = 12
	final val Veteran = 13
	final val Guest = 10

	/** The set of every user having developer rights */
	val developers = Set(1647)

	/** The set of every groups considered officer */
	val officers = Set.empty + Officer

	/** The set of every groups considered guild members */
	val members = officers + Member

	/** The set of every groups forming the guild roster */
	val roster = members + Apply

	/** The set of every groups considered part of the guild */
	val fromscratch = roster + Casual + Veteran + Guest
}
