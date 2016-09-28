package gt.services

import boopickle.DefaultBasic._
import gt.services.base.{Cache, Delegate, Service}
import models.StreamStatus
import scala.concurrent.Future

object StreamService extends Service with Delegate {
	val channel = registerChannel("stream", lzy = false)

	object streams extends Cache((s: StreamStatus) => s.user) {

	}

	message("list") { list: Seq[StreamStatus] =>
		list.foreach(streams.update)
		println(streams.values.!)
	}

	message("notify")(streams.update _)
	message("offline")(streams.removeKey _)

	def requestTicket(user: Int): Future[(String, String)] = {
		channel.request("request-ticket", user).as[(String, String)]
	}
}
