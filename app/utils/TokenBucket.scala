package utils

/**
  * Implementation of a token bucket
  *
  * @param max       the maximum number of token this bucket can hold
  * @param interval  the interval between token generation in milliseconds
  */
class TokenBucket(max: Int, interval: Int) {
	/** Compute the fill rate from the generation interval */
	private val rate = 1.0 / interval

	/** Current amount of tokens in the bucket */
	private var amount: Double = max.toDouble

	/** Last update timestamp */
	private var update: Long = System.currentTimeMillis()

	/**
	  * Removes a token from this bucket.
	  * return false if no tokens are available
	  */
	def take(): Boolean = synchronized {
		val now = System.currentTimeMillis()
		val dt = now - update
		update = now

		// Fill the bucket
		amount = Math.min(max, amount + dt * rate)

		if (amount > 1.0) {
			amount -= 1.0
			true
		} else {
			false
		}
	}

	/** Restores a token in the bucket */
	def put(tokens: Double = 1.0) = synchronized {
		amount = Math.min(max, amount + tokens)
	}
}
