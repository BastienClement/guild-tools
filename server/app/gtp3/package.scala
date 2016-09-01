import channels._

package object gtp3 {
	object ProtocolError extends Throwable
	final val GTP3Magic = 0x47545033

	case class Error(message: String, code: Int = 0, cause: Throwable = null) extends Exception(message, cause) {
		require(message != null)
	}

	val ChannelValidators = Map[String, ChannelValidator](
		"apply" -> ApplyChannel,
		"auth" -> AuthChannel,
		"calendar" -> CalendarChannel,
		"calendar-event" -> CalendarEventChannel,
		"composer" -> ComposerChannel,
		"composer-document" -> composer.DocumentChannel,
		"chat" -> ChatChannel,
		"master" -> MasterChannel,
		"newsfeed" -> NewsFeedChannel,
		"profile" -> ProfileChannel,
		"roster" -> RosterChannel,
		"server-status" -> ServerStatusChannel,
		"stream" -> StreamChannel
	)

	class TooManyBuffersException extends Exception
	private[gtp3] val pool = new BufferPool
}
