package utils.intervaltree

case class Interval[H, +A](lower: H, upper: H, data: A, left: IntervalTree[H, A], right: IntervalTree[H, A])
                          (implicit ord: Ordering[H], val empty: IntervalTree[H, A]) extends IntervalTree[H, A] {
	// Operations on ordered types
	import IntervalTree.OrderingOps

	val balance: Int = right.depth - left.depth
	val depth: Int = (left.depth max right.depth) + 1

	val min: H = left combinedMin (right combinedMin lower)
	protected[intervaltree] def combinedMin(lo: H): H = lo min min

	val max: H = left combinedMax (right combinedMax upper)
	protected[intervaltree] def combinedMax(up: H): H = up max max

	@inline private[this] final def isLeaf[B >: A](node: IntervalTree[H, B]): Boolean = node.depth == -1
	@inline private[this] final def asInterval[B >: A](node: IntervalTree[H, B]): Interval[H, B] = node.asInstanceOf[Interval[H, A]]

	def insert[B >: A](lo: H, up: H, data: B): IntervalTree[H, B] = {
		val ol = lo compare lower
		val ou = up compare upper
		if (ol == 0 && ou == 0) {
			copy(data = data)
		} else if (ol < 0 || (ol == 0 && ou < 0)) {
			balanced(left = left.insert(lo, up, data))
		} else {
			balanced(right = right.insert(lo, up, data))
		}
	}

	def get(lo: H, up: H): Option[A] = {
		val ol = lo compare lower
		val ou = up compare upper
		if (ol == 0 && ou == 0) {
			Some(data)
		} else if (ol < 0 || (ol == 0 && ou < 0)) {
			left.get(lo, up)
		} else {
			right.get(lo, up)
		}
	}

	def remove(lo: H, up: H): IntervalTree[H, A] = {
		val ol = lo compare lower
		val ou = up compare upper
		if (ol == 0 && ou == 0) {
			if (isLeaf(left)) {
				if (isLeaf(right)) {
					empty
				} else {
					val (min, newRight) = asInterval(right).withoutMin
					balanced(min.lower, min.upper, min.data, left, newRight)
				}
			} else {
				val (max, newLeft) = asInterval(left).withoutMax
				balanced(max.lower, max.upper, max.data, newLeft, right)
			}
		} else if (ol < 0 || (ol == 0 && ou < 0)) {
			balanced(left = left.remove(lo, up))
		} else {
			balanced(right = right.remove(lo, up))
		}
	}

	@inline final def withoutMin: (Interval[H, A], IntervalTree[H, A]) = {
		if (isLeaf(left)) (this, right)
		else {
			val (min, newLeft) = asInterval(left).withoutMin
			(min, balanced(left = newLeft))
		}
	}

	@inline final def withoutMax: (Interval[H, A], IntervalTree[H, A]) = {
		if (isLeaf(right)) (this, right)
		else {
			val (max, newRight) = asInterval(right).withoutMax
			(max, balanced(right = newRight))
		}
	}

	@inline private[this] def balanced[B >: A](lower: H = lower, upper: H = upper, data: B = data,
	                                           left: IntervalTree[H, B] = left,
	                                           right: IntervalTree[H, B] = right): IntervalTree[H, B] = {
		val balance = right.depth - left.depth
		if (balance == -2) {
			if (left.balance == 1) leftRightRotation(lower, upper, data, left, right)
			else rightRotation(lower, upper, data, left, right)
		} else if (balance == 2) {
			if (right.balance == -1) rightLeftRotation(lower, upper, data, left, right)
			else leftRotation(lower, upper, data, left, right)
		} else if ((lower equiv this.lower) && (upper equiv this.upper) && data == this.data && (left eq this.left) && (right eq this.right)) {
			this
		} else {
			Interval(lower, upper, data, left, right)
		}
	}

	@inline private[this] final def leftRotation[B >: A](lower: H, upper: H, data: B, left: IntervalTree[H, B],
	                                                     right: IntervalTree[H, B]): Interval[H, B] = {
		val r = asInterval(right)
		Interval(r.lower, r.upper, r.data, Interval(lower, upper, data, left, r.left), r.right)
	}

	@inline private[this] final def rightRotation[B >: A](lower: H, upper: H, data: B, left: IntervalTree[H, B],
	                                                      right: IntervalTree[H, B]): Interval[H, B] = {
		val l = asInterval(left)
		Interval(l.lower, l.upper, l.data, l.left, Interval(lower, upper, data, l.right, right))
	}

	@inline private[this] final def rightLeftRotation[B >: A](lower: H, upper: H, data: B, left: IntervalTree[H, B],
	                                                          right: IntervalTree[H, B]): Interval[H, B] = {
		val r = asInterval(right)
		val rr = rightRotation(r.lower, r.upper, r.data, r.left, r.right)
		Interval(rr.lower, rr.upper, rr.data, Interval(lower, upper, data, left, rr.left), rr.right)
	}

	@inline private[this] final def leftRightRotation[B >: A](lower: H, upper: H, data: B, left: IntervalTree[H, B],
	                                                          right: IntervalTree[H, B]): Interval[H, B] = {
		val l = asInterval(left)
		val ll = leftRotation(l.lower, l.upper, l.data, l.left, l.right)
		Interval(ll.lower, ll.upper, ll.data, ll.left, Interval(lower, upper, data, ll.right, right))
	}

	@inline private[this] final def select[B >: A](lo: H, up: H, base: Vector[B] = Vector.empty)
	                                              (ignore: (H, H, H, H) => Boolean,
	                                               cond: (H, H, H, H) => Boolean,
	                                               cont: (IntervalTree[H, A]) => (H, H, Vector[B]) => Vector[B]): Vector[B] = {
		if (ignore(lo, up, min, max)) base
		else {
			var vec = cont(left)(lo, up, base)
			if (cond(lo, up, lower, upper)) vec = vec :+ data
			cont(right)(lo, up, vec)
		}
	}

	def overlapping[B >: A](lo: H, up: H, base: Vector[B] = Vector.empty): Vector[B] = select(lo, up, base)(
		(lo, up, min, max) => up < min || lo > max,
		(lo, up, lower, upper) => up >= lower && lo <= upper,
		t => t.overlapping
	)

	def containing[B >: A](lo: H, up: H, base: Vector[B] = Vector.empty): Vector[B] = select(lo, up, base)(
		(lo, up, min, max) => lo < min || up > max,
		(lo, up, lower, upper) => lo >= lower && up <= upper,
		t => t.containing
	)

	def contained[B >: A](lo: H, up: H, base: Vector[B] = Vector.empty): Vector[B] = select(lo, up, base)(
		(lo, up, min, max) => lo > min || up < max,
		(lo, up, lower, upper) => lo <= lower && up >= upper,
		t => t.contained
	)
}
