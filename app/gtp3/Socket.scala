package gtp3

class Socket(val id: Long, var actor: SocketActor) {
	def receive(buffer: Array[Byte]) = ???
	def attach(actor: SocketActor) = ???
	def detach() = ???
}
