package channels

import actors.SocketManager
import akka.actor.Props
import api.Status
import boopickle.DefaultBasic._
import gt.GuildTools
import gtp3._
import scala.concurrent.duration._
import scala.sys.process._
import scala.util.Try
import utils.CacheCell

object ServerStatusChannel extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.user.promoted) request.accept(Props(new ServerStatusChannel))
		else request.reject(1, "Unauthorized")
	}
}

class ServerStatusChannel extends ChannelHandler {
	// Run a shell command in a safe way
	def run(cmd: String): String = Try { cmd.!!.trim } getOrElse "n/a"

	// Cache of host information
	val hostInfos = CacheCell(15.seconds) {
		Status.HostInfo(
			name = run("hostname"),
			version = run("uname -a"),
			start = 0,
			uptime = run("uptime")
		)
	}

	// Server software infos
	request("server-infos") {
		Status.ServerInfo(
			name = GuildTools.serverName,
			version = GuildTools.serverVersion.value,
			start = GuildTools.serverStart,
			uptime = GuildTools.serverUptime
		)
	}

	// Host machine infos
	request("host-infos") {
		hostInfos.value
	}

	// JVM runtime infos
	request("runtime-infos") {
		val runtime = Runtime.getRuntime
		Status.RuntimeInfo(
			cores = runtime.availableProcessors,
			memoryUsed = runtime.totalMemory - runtime.freeMemory,
			memoryFree = runtime.freeMemory,
			memoryTotal = runtime.totalMemory,
			memoryMax = runtime.maxMemory
		)
	}

	// Open socket infos
	/*request("sockets-infos") { _ =>
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
	}*/

	// Kill socket
	message("kill-socket") { sockid: Long => SocketManager.killSocket(sockid) }

	// Run the garbage collector
	request("run-gc") { System.gc() }
}
