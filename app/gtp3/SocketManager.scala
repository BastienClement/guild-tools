package gtp3

import java.security.SecureRandom

import scala.annotation.tailrec
import scala.collection.mutable

object SocketManager {
	private val sockets = mutable.Map[Long, Socket]()

	private val rand = new SecureRandom()()
	private def nextSocketID = this.synchronized {
		@tailrec
		def search(): Long = {
			val id = rand.nextLong()
			if (sockets contains id) search()
			else id
		}

		search()
	}


	def allocate(actor: SocketActor): Socket = {

	}
}
