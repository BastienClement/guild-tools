package actors

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.reflect.ClassTag
import akka.actor.{DeadLetter, Props, TypedActor, TypedProps}
import play.api.Play.current
import play.api.libs.concurrent.Akka

object Actors {
	private def initActor[I <: AnyRef, A <: I](n: String)(implicit ct: ClassTag[A]): I = {
		TypedActor(Akka.system).typedActorOf(TypedProps[A], name = n)
	}

	var DeadLetterLogger = Akka.system.actorOf(Props[DeadLetterLogger], "DeadlettersLogger")
	Akka.system.eventStream.subscribe(DeadLetterLogger, classOf[DeadLetter])

	val AuthService = initActor[AuthService, AuthServiceImpl]("AuthService")
	val BattleNet = initActor[BattleNet, BattleNetImpl]("BattleNet")
	val CalendarLocks = initActor[CalendarLocks, CalendarLocksImpl]("CalendarLocks")
	val ChatService = initActor[ChatService, ChatServiceImpl]("ChatService")
	val ComposerService = initActor[ComposerService, ComposerServiceImpl]("ComposerService")
	val Dispatcher = initActor[Dispatcher, DispatcherImpl]("Dispatcher")
	val RosterService = initActor[RosterService, RosterServiceImpl]("RosterService")

	object Implicits {
		implicit def FutureBoxing[T](v: T): Future[T] = Future.successful(v)
		implicit def FutureBoxing[T](v: Option[T]): Future[T] =
			if (v.isDefined) Future.successful(v.get)
			else Future.failed(new NoSuchElementException)
	}
}
