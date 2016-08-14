package util.intervaltree

case class Empty[H](implicit ord: Ordering[H]) extends IntervalTree[H, Nothing] {
	val balance: Int = 0
	val depth: Int = -1
	val empty: IntervalTree[H, Nothing] = this

	protected[intervaltree] def combinedMin(lo: H): H = lo

	protected[intervaltree] def combinedMax(up: H): H = up

	def insert[B >: Nothing](lo: H, up: H, data: B): IntervalTree[H, B] = {
		Interval(lo, up, data, this, this)(ord, this)
	}

	def get(lo: H, up: H): Option[Nothing] = None

	override def has(lo: H, up: H): Boolean = false

	def remove(lo: H, up: H): IntervalTree[H, Nothing] = this

	def overlapping[B >: Nothing](lo: H, up: H, base: Vector[B] = Vector.empty): Vector[B] = base
	def containing[B >: Nothing](lo: H, up: H, base: Vector[B] = Vector.empty): Vector[B] = base
	def contained[B >: Nothing](lo: H, up: H, base: Vector[B] = Vector.empty): Vector[B] = base
}
