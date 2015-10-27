package channels

import actors.SocketManager
import akka.actor.Props
import akka.pattern._
import akka.util.Timeout
import gt.GuildTools
import gtp3.Socket.{RequestInfos, SocketInfos}
import gtp3._
import play.api.libs.json.{JsNull, Json}
import reactive._
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.sys.process._
import scala.util.Try
import utils.CacheCell

object ServerStatus extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.user.promoted) request.accept(Props(new ServerStatus))
		else request.reject(1, "Unauthorized")
	}
}

class ServerStatus extends ChannelHandler {
	// Run a shell command in a safe way
	def run(cmd: String): String = Try { cmd.!!.trim } getOrElse "n/a"

	// Cache of host information
	val hostInfos = CacheCell(15.seconds) {
		Json.obj(
			"name" -> run("hostname"),
			"version" -> run("uname -a"),
			"start" -> 0,
			"uptime" -> run("uptime")
		)
	}

	// Server software infos
	request("server-infos") { _ =>
		Json.obj(
			"name" -> GuildTools.serverName,
			"version" -> GuildTools.serverVersion.value,
			"start" -> GuildTools.serverStart,
			"uptime" -> GuildTools.serverUptime
		)
	}

	// Host machine infos
	request("host-infos") { _ => hostInfos.value }

	// JVM runtime infos
	request("runtime-infos") { _ =>
		val runtime = Runtime.getRuntime
		Json.obj(
			"cores" -> runtime.availableProcessors,
			"memory_used" -> (runtime.totalMemory - runtime.freeMemory),
			"memory_free" -> runtime.freeMemory,
			"memory_total" -> runtime.totalMemory,
			"memory_max" -> runtime.maxMemory
		)
	}

	// Open socket infos
	request("sockets-infos") { _ =>
		implicit val timeout = Timeout(3.seconds)
		SocketManager.socketsMap flatMap { sockets =>
			Future.sequence {
				for ((id, socket) <- sockets.toSeq) yield {
					for (infos <- (socket ? RequestInfos).mapTo[SocketInfos]) yield {
						Json.obj(
							"id" -> id.toString,
							"name" -> socket.path.toStringWithoutAddress,
							"user" -> (if (infos.user != null) infos.user else JsNull),
							"state" -> infos.state,
							"uptime" -> (Platform.currentTime - infos.open),
							"opener" -> Json.obj(
								"ip" -> infos.opener.ip,
								"ua" -> infos.opener.ua
							),
							"channels" -> infos.channels
						)
					}
				}
			}
		}
	}

	// Kill socket
	message("kill-socket") { p =>
		SocketManager.killSocket(p.string.toLong)
	}

	// Run the garbage collector
	request("run-gc") { _ => System.gc() }
}
