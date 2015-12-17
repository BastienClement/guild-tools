import channels._

package object gtp3 {
	object ProtocolError extends Throwable
	final val GTP3Magic = 0x47545033

	case class Error(message: String, code: Int = 0, cause: Throwable = null) extends Exception(message, cause) {
		require(message != null)
	}

	val ChannelValidators = Map[String, ChannelValidator](
		"apply" -> Apply,
		"auth" -> Auth,
		"chat" -> Chat,
		"master" -> Master,
		"newsfeed" -> NewsFeed,
		"profile" -> Profile,
		"roster" -> Roster,
		"server-status" -> ServerStatus,
		"stream" -> Stream
	)

	class TooManyBuffersException extends Exception
	private[gtp3] val pool = new BufferPool
}
