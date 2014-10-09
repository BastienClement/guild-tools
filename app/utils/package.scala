import java.math.BigInteger
import java.security.{ MessageDigest, SecureRandom }
import scala.async.Async._
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import api.MessageDeferred
import api.MessageResults
import gt.Global.ExecutionContext
import play.libs.Akka
import api.MessageDeferred
import api.MessageResponse

package object utils {
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

	/**
	 * Deferred MessageResult generator
	 */
	def defer(body: => MessageResponse): MessageDeferred = defer(Future { body })
	def defer(future: Future[MessageResponse]): MessageDeferred = MessageDeferred(future)
}
