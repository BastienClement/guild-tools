package gt

import java.math.BigInteger
import java.security.{MessageDigest, SecureRandom}

import play.libs.Akka

object Utils {
	// Generate random tokens
	val secureRandom = new SecureRandom
	def randomToken() = new BigInteger(130, secureRandom).toString(32)

	// Return the Akka system schduler
	def scheduler = Akka.system().scheduler

	// Execute some code with the given value and then return that value
	def use[T](v: T)(body: (T) => Unit): T = { body(v); v }

	// MD5 hash with hex string result
	def md5(data: String): String = {
		val bytes = MessageDigest.getInstance("MD5").digest(data.getBytes)
		bytes.map("%1$02x".format(_)).mkString
	}
}
