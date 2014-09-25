import scala.util.Random
import java.security.SecureRandom
import java.math.BigInteger

object Test {
	import java.security.MessageDigest

	def md5(s: String) = {
		val bytes = MessageDigest.getInstance("MD5").digest(s.getBytes)
		bytes.map("%1$02x".format(_)).mkString
	}                                         //> md5: (s: String)String

	md5("The quick brown fox jumps over the lazy dog")
                                                  //> res0: String = 9e107d9d372bb6826bd81d3542a419d6
}