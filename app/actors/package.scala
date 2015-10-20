import akka.actor._
import play.api.Logger
import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.ClassTag
import play.api.Play.current

package object actors {
	// Construct persistant TypedActors
	private[actors] abstract class StaticActor[A <: AnyRef, B <: A : ClassTag](name: String, timeout: FiniteDuration = 1.minute) {
		private[actors] val $actor: A = TypedActor(Akka.system).typedActorOf(TypedProps[B].withTimeout(timeout), name)
	}

	// Extract the inner TypedActor from the companion object
	implicit def invokeTypedActor[A <: AnyRef](s: StaticActor[A, _]): A = s.$actor

	// Log DeadLetters
	private class DeadLetterLogger extends Actor {
		def receive = {
			case DeadLetter(msg, from, to) =>
				Logger.warn(s"DeadLetter from $from to $to -> $msg")
		}
	}

	// Subscribe the logger to the event stream
	private val DeadLetterLogger = Akka.system.actorOf(Props[DeadLetterLogger], "DeadlettersLogger")
	Akka.system.eventStream.subscribe(DeadLetterLogger, classOf[DeadLetter])
}
