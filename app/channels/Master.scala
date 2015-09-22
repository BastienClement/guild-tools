package channels

import gt.Global.ExecutionContext
import gtp3._
import models._
import models.mysql._

import scala.concurrent.Future

object Master extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(new Master)
}

class Master extends ChannelHandler {
	request("get-config") { payload =>
		val q = Configs filter (_.user === user.id) take 1
		for (conf <- q.head) yield conf.jsvalue
	}

	message("set-config") { payload =>
		val c = Config(user.id, payload.string)
		Configs insertOrUpdate c
	}
}
