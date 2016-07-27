package gtp3

private[gtp3] sealed trait ChannelState

private[gtp3] object ChannelState {
	case object Open extends ChannelState
	case object Closed extends ChannelState
}
