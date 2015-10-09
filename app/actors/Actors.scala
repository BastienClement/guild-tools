package actors

import akka.actor.{DeadLetter, Props, TypedActor, TypedProps}
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.ClassTag

object Actors {
	private def initActor[I <: AnyRef, A <: I : ClassTag](n: String, timeout: FiniteDuration = 1.minute): I = {
		TypedActor(Akka.system).typedActorOf(TypedProps[A].withTimeout(timeout), name = n)
	}

	var DeadLetterLogger = Akka.system.actorOf(Props[DeadLetterLogger], "DeadlettersLogger")
	Akka.system.eventStream.subscribe(DeadLetterLogger, classOf[DeadLetter])

	val AuthService = initActor[AuthService, AuthServiceImpl]("AuthService")
	val BattleNet = initActor[BattleNet, BattleNetImpl]("BattleNet")
	val CalendarService = initActor[CalendarService, CalendarServiceImpl]("CalendarService")
	val ChatService = initActor[ChatService, ChatServiceImpl]("ChatService")
	val ComposerService = initActor[ComposerService, ComposerServiceImpl]("ComposerService")
	val RosterService = initActor[RosterService, RosterServiceImpl]("RosterService")
	val SocketManager = initActor[SocketManager, SocketManagerImpl]("SocketManager")
}
