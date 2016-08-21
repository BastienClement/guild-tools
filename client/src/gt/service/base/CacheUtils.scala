package gt.service.base

object CacheUtils {
	/** A dummy type for the hash function to help disambiguation */
	case class LiftedHashFunction[V, H](hash: V => Option[H]) {
		@inline final def apply(value: V): Option[H] = hash(value)
	}
}
