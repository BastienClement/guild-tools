package gt

import Global.ExecutionContext
import java.math.BigInteger
import java.security.{ MessageDigest, SecureRandom }
import play.libs.Akka
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.{ Failure, Success }

object Utils {
	/**
	 * Generate random tokens
	 */
	val secureRandom = new SecureRandom
	def randomToken(): String = new BigInteger(130, secureRandom).toString(32)

	/**
	 * Return the Akka system schduler
	 */
	def scheduler = Akka.system().scheduler

	/**
	 * Execute some async code with the given value and return that value
	 */
	def using[T](value: T)(body: T => Unit): T = {
		Future { body(value) }
		value
	}

	/**
	 * MD5 hash with hex string result
	 */
	def md5(data: String): String = {
		val bytes = MessageDigest.getInstance("MD5").digest(data.getBytes)
		val parts = bytes map ("%1$02x".format(_))
		parts.mkString
	}

	/**
	 * Do not return before a minimum duration
	 */
	def atLeast[T](d: FiniteDuration)(body: => T): T = {
		val task = Future { body }
		val p = Promise[T]()

		scheduler.scheduleOnce(d) {
			task onComplete {
				case Success(v) => p.success(v)
				case Failure(e) => p.failure(e)
			}
		}

		Await.result(p.future, Duration.Inf)
	}
}
