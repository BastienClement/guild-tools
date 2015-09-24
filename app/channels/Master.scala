package channels

import gt.Global.ExecutionContext
import gtp3._
import models._
import models.mysql._
import play.api.libs.json.Json

import scala.concurrent.Future

object Master extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(new Master)
}

class Master extends ChannelHandler {
	// Request the previously saved configuration object
	request("get-config") { payload =>
		val q = Configs filter (_.user === user.id) take 1
		for (data <- q.headOption) yield data match {
			case Some(config) => config.jsvalue
			case None => Json.obj()
		}
	}

	// Save a configuration object for this user
	message("set-config") { payload =>
		// Check if we actually have textual data to store
		if (!payload.utf8_data)
			throw new Exception("Invalid configuration data given, nothing stored")

		// Payload is probably a complex JSON object but read it as a string
		val conf = payload.string

		// Enforce a 512KB limit on data stored by users
		if (conf.length > 524288)
			throw new Exception("Configuration storage is currently limited to 512KB per user.")

		DB.run(Configs insertOrUpdate Config(user.id, conf))
	}
}
