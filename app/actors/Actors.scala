package actors

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.reflect.ClassTag
import akka.actor.{TypedActor, TypedProps}
import play.api.Play.current
import play.api.libs.concurrent.Akka

object Actors {
	private def initActor[I <: AnyRef, A <: I](n: String)(implicit ct: ClassTag[A]): I = {
		TypedActor(Akka.system).typedActorOf(TypedProps[A], name = n)
	}

	val Authenticator = initActor[Authenticator, AuthenticatorImpl]("Authenticator")
	val BattleNet = initActor[BattleNet, BattleNetImpl]("BattleNet")
	val CalendarLocks = initActor[CalendarLocks, CalendarLocksImpl]("CalendarLockManager")
	val Chat = initActor[Chat, ChatImpl]("ChatManager")
	val Dispatcher = initActor[Dispatcher, DispatcherImpl]("EventDispatcher")
	val Roster = initActor[Roster, RosterImpl]("RosterManager")

	object Implicits {
		implicit def FutureBoxing[T](v: T): Future[T] = Future.successful(v)
		implicit def FutureBoxing[T](v: Option[T]): Future[T] =
			if (v.isDefined) Future.successful(v.get)
			else Future.failed(new NoSuchElementException)
	}
}
