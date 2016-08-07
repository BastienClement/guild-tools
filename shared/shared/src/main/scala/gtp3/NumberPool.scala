package gtp3

import scala.collection.mutable

class NumberPool(val limit: Int = 0xFFFF) {
	private var max: Int = 0
	private val allocated = mutable.Set[Int]()
	private val released = mutable.Queue[Int]()

	private def selectNext() = {
		if (released.nonEmpty) {
			released.dequeue()
		} else if (max < limit) {
			max += 1
			max
		} else {
			throw new Exception("Unable to allocate next number (limit reached)")
		}
	}

	def next = {
		val n = selectNext()
		allocated.add(n)
		n
	}

	def release(n: Int) = {
		if (allocated.remove(n)) {
			this.released.enqueue(n)
		}
	}

	def clear() = {
		max = 0
		allocated.clear()
		released.clear()
	}
}
