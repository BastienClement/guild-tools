import channels._
import scala.collection.immutable

package object gtp3 {
	object ProtocolError extends Throwable
	final val GTP3Magic = 0x47545033

	val ChannelAcceptors = immutable.Map[String, ChannelAcceptor](
		"auth" -> Auth
	)
}
