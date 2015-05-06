package gtp3

import java.security.SecureRandom

import scala.collection.mutable

object SocketManager {
	private val sockets = mutable.Map[Long, Socket]()

	private val rand = new SecureRandom()
	private def nextSocketID = this.synchronized {
		var id: Long = 0
		do {
			id = rand.nextLong()
		} while (sockets contains id)
		id
	}


	def allocate(actor: SocketActor): Socket = {

	}
}
