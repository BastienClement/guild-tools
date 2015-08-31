package utils

import gtp3.{Payload, Channel}

trait ChannelList[T] {
	protected var channels = Map[Channel, T]()

	protected type Filter = (T) => Boolean
	protected def default(data: T) = true

	protected def broadcast(msg: String, pyld: Payload, filter: Filter = default) = {
		for ((chan, data) <- channels.par if filter(data))
			chan.send(msg, pyld)
	}

	protected def multicast(targets: Iterable[Channel], msg: String, pyld: Payload, filter: Filter = default) = {
		for (chan <- targets.par if channels.get(chan).exists(filter))
			chan.send(msg, pyld)
	}

	protected def registerChannel(chan: Channel, data: T) = channels += chan -> data
	protected def unregisterChannel(chan: Channel) = channels -= chan
}
