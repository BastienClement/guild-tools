package actors

import scala.reflect.ClassTag
import akka.actor.{Props, TypedActor, TypedProps}
import play.api.libs.concurrent.Akka
import gt.Global.ExecutionContext
import play.api.Play.current

/**
 * Created by Galedric on 07/11/14.
 */
object Actors {
	private def initActor[I <: AnyRef, A <: I](n: String)(implicit ct: ClassTag[A]): I = {
		TypedActor(Akka.system).typedActorOf(TypedProps[A], name = n)
	}

	val CalendarLockManager = initActor[CalendarLockManagerInterface, CalendarLockManager]("CalendarLockManager")
	val ChatManager = initActor[ChatManagerInterface, ChatManager]("ChatManager")
	val RosterManager = initActor[RosterManagerInterface, RosterManager]("RosterManager")
}
