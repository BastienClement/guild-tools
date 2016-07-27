package gtp3

private[gtp3] sealed trait SocketState

private[gtp3] object SocketState {
	case object Uninitialized extends SocketState
	case object Open extends SocketState
	case object Ready extends SocketState
	case object Reconnecting extends SocketState
	case object Closed extends SocketState
}
