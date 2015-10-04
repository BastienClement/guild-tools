package actors

import akka.actor.{DeadLetter, Props, TypedActor, TypedProps}
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.ClassTag

object Actors {
	private def initActor[I <: AnyRef, A <: I](n: String, timeout: FiniteDuration = 1.minute)(implicit ct: ClassTag[A]): I = {
		TypedActor(Akka.system).typedActorOf(TypedProps[A].withTimeout(timeout), name = n)
	}

	var DeadLetterLogger = Akka.system.actorOf(Props[DeadLetterLogger], "DeadlettersLogger")
	Akka.system.eventStream.subscribe(DeadLetterLogger, classOf[DeadLetter])

	val AuthService = initActor[AuthService, AuthServiceImpl]("AuthService")
	val BattleNet = initActor[BattleNet, BattleNetImpl]("BattleNet")
	val CalendarService = initActor[CalendarService, CalendarServiceImpl]("CalendarService")
	val ChatService = initActor[ChatService, ChatServiceImpl]("ChatService")
	val ComposerService = initActor[ComposerService, ComposerServiceImpl]("ComposerService")
	val Dispatcher = initActor[Dispatcher, DispatcherImpl]("Dispatcher")
	val RosterService = initActor[RosterService, RosterServiceImpl]("RosterService")
	val SocketManager = initActor[SocketManager, SocketManagerImpl]("SocketManager")

	trait ActorImplicits {
		implicit def FutureBoxing[T](v: T): Future[T] = Future.successful(v)
		implicit def FutureBoxing[T](v: Option[T]): Future[T] =
			try {
				Future.successful(v.get)
			} catch {
				case e: Throwable => Future.failed(e)
			}
	}
}
