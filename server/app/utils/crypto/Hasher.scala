package utils.crypto

object Hasher {
	/**
	  * Check the equality between a password and the reference hash.
	  * Hashing algorithm will be selected automatically.
	  *
	  * @param pass the raw password
	  * @param ref  the reference hash
	  */
	def checkPassword(pass: String, ref: String): Boolean = {
		if (ref.startsWith("$H$")) {
			val hash = new Phpass
			hash.isMatch(pass, ref)
		} else if (ref.startsWith("$2a$")) {
			BCrypt.checkpw(pass, ref)
		} else {
			false
		}
	}
}
