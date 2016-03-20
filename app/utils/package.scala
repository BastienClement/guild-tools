import gt.GuildTools
import java.math.BigInteger
import java.security.{MessageDigest, SecureRandom}
import reactive.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.language.{higherKinds, implicitConversions}
import scala.util.{Failure, Success}

package object utils {
	/**
	  * Generate random tokens
	  */
	val secureRandom = new SecureRandom
	def randomToken(): String = new BigInteger(320, secureRandom).toString(32)

	/**
	  * Return the Akka system schduler
	  */
	lazy val scheduler = GuildTools.system.scheduler

	/**
	  * MD5 hash with hex string result
	  */
	def md5(data: String): String = {
		val bytes = MessageDigest.getInstance("MD5").digest(data.getBytes)
		val parts = bytes map ("%1$02x".format(_))
		parts.mkString
	}

	/**
	  * SHA1 hash with hex string result
	  */
	def sha1(data: String): String = {
		val bytes = MessageDigest.getInstance("SHA1").digest(data.getBytes)
		val parts = bytes map ("%1$02x".format(_))
		parts.mkString
	}

	/**
	  * Do not return before a minimum duration
	  */
	def atLeast[T](d: FiniteDuration)(body: => T): T = {
		val task = Future {
			body
		}
		val p = Promise[T]()

		scheduler.scheduleOnce(d) {
			task onComplete {
				case Success(v) => p.success(v)
				case Failure(e) => p.failure(e)
			}
		}

		Await.result(p.future, Duration.Inf)
	}

	/**
	  * Do something conditionally but return the boolean value
	  */
	def doIf(pred: => Boolean)(body: => Unit): Boolean = {
		if (pred) {
			body
			true
		} else {
			false
		}
	}
}
