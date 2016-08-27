package channels

import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3._
import models.User
import models._
import models.mysql._

object MasterChannel extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new MasterChannel(request.user)))
}

class MasterChannel(val user: User) extends ChannelHandler {
	// Request the previously saved configuration object
	request("get-config") {
		Configs.filter(_.user === user.id).map(_.data).take(1).headOption
	}

	// Save a configuration object for this user
	request("set-config") { conf: String =>
		// Enforce a 512KB limit on data stored by users
		if (conf.length > 524288) throw new Exception("Configuration storage is currently limited to 512KB per user")
		Configs insertOrUpdate Config(user.id, conf)
	}
}
