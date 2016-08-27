package utils.intervaltree

import scala.language.higherKinds

/**
  * An immutable interval tree.
  *
  * @tparam H the type of the interval values
  * @tparam A the type of the associated data
  */
trait IntervalTree[H, +A] {
	/** AVL balance of this node */
	def balance: Int

	/** AVL depth of this node */
	def depth: Int

	/** Returns an empty IntervalTree of the same type */
	def empty: IntervalTree[H, A]

	/** Returns the combined max between the node max and the given upper bound */
	protected[intervaltree] def combinedMin(up: H): H

	/** Returns the combined max between the node max and the given upper bound */
	protected[intervaltree] def combinedMax(up: H): H

	/**
	  * Inserts an interval with associated data into the tree.
	  *
	  * If this interval is already present in the tree, its associated values is updated.
	  *
	  * @param lo   the lower bound of the interval
	  * @param up   the upper bound of the interval
	  * @param data the associated data to store
	  * @tparam B the type of associated data in the resulting tree
	  */
	def insert[B >: A](lo: H, up: H, data: B): IntervalTree[H, B]

	/**
	  *
	  * @param lo
	  * @param up
	  * @return
	  */
	def get(lo: H, up: H): Option[A]

	def apply(lo: H, up: H): A = get(lo, up).get

	/**
	  *
	  * @param lo
	  * @param up
	  * @return
	  */
	def has(lo: H, up: H): Boolean = get(lo, up).isDefined

	/**
	  * Removes the given interval from the tree.
	  *
	  * If this interval is not present in the tree, this operation is a no-op.
	  *
	  * @param lo the lower bound of the interval
	  * @param up the upper bound of the interval
	  */
	def remove(lo: H, up: H): IntervalTree[H, A]

	def overlapping[B >: A](lo: H, up: H, base: Vector[B] = Vector.empty): Vector[B]
	def containing[B >: A](lo: H, up: H, base: Vector[B] = Vector.empty): Vector[B]
	def contained[B >: A](lo: H, up: H, base: Vector[B] = Vector.empty): Vector[B]
}

object IntervalTree {
	def apply[H: Ordering, A](elems: ((H, H), A)*): IntervalTree[H, A] = {
		(empty[H, A] /: elems) ((t, e) => t.insert(e._1._1, e._1._2, e._2))
	}

	def empty[H: Ordering, A]: IntervalTree[H, A] = Empty[H]

	implicit final class OrderingOps[T](private val lhs: T) extends AnyVal {
		@inline def equiv(rhs: T)(implicit ord: Ordering[T]): Boolean = ord.equiv(lhs, rhs)
		@inline def < (rhs: T)(implicit ord: Ordering[T]): Boolean = ord.lt(lhs, rhs)
		@inline def <= (rhs: T)(implicit ord: Ordering[T]): Boolean = ord.lteq(lhs, rhs)
		@inline def > (rhs: T)(implicit ord: Ordering[T]): Boolean = ord.gt(lhs, rhs)
		@inline def >= (rhs: T)(implicit ord: Ordering[T]): Boolean = ord.gteq(lhs, rhs)
		@inline def compare(rhs: T)(implicit ord: Ordering[T]): Int = ord.compare(lhs, rhs)
		@inline def min(rhs: T)(implicit ord: Ordering[T]): T = ord.min(lhs, rhs)
		@inline def max(rhs: T)(implicit ord: Ordering[T]): T = ord.max(lhs, rhs)
	}

	def formatTree(tree: IntervalTree[_, _], level: Int = 0, label: String = ""): String = {
		val indent = (if (level < 1) "" else "  " * (level - 1) + " |-") + label
		tree match {
			case _: Empty[_] => s"$indent<>"
			case i @ Interval(lower, upper, data, left, right) =>
				s"$indent[$lower; $upper] (${ data.toString }) max=${ i.max } b=${ i.balance } d=${ i.depth }\n${ formatTree(right, level + 1, "R = ") }\n${ formatTree(left, level + 1, "L = ") }"
		}
	}
}
