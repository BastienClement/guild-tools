package api

import boopickle.DefaultBasic._
import utils.annotation.data

object Status {
	@data case class ServerInfo(name: String, version: String, start: Long, uptime: Long)
	@data case class HostInfo(name: String, version: String, start: Long, uptime: String)
	@data case class RuntimeInfo(cores: Int, memoryUsed: Long, memoryFree: Long, memoryTotal: Long, memoryMax: Long)

	implicit val ServerInfoPickler = PicklerGenerator.generatePickler[ServerInfo]
	implicit val HostInfoPickler = PicklerGenerator.generatePickler[HostInfo]
	implicit val RuntimeInfoPickler = PicklerGenerator.generatePickler[RuntimeInfo]
}
