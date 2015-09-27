import channels._
import scala.collection.immutable

package object gtp3 {
	object ProtocolError extends Throwable
	final val GTP3Magic = 0x47545033

	val ChannelValidators = Map[String, ChannelValidator](
		"auth" -> Auth,
		"chat" -> Chat,
		"master" -> Master,
		"newsfeed" -> NewsFeed,
		"roster" -> Roster
	)
}
