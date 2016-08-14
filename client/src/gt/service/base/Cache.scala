package gt.service.base

import rx.{Obs, Rx, Var}
import scala.collection.immutable.TreeMap
import util.intervaltree.IntervalTree

abstract class Cache[K, V <: AnyRef](hash: V => K) {
	/** Default value for newly allocated cells */
	def default(key: K): V = throw new UnsupportedOperationException("Default value factory is not defined")

	/** Collection of items in the cache */
	private var items = Map[K, Var[V]]()
	private var indexes = Set[BaseIndex]()

	private def constructCell(key: K, value: V) = {
		val cell = Var[V](value)
		items += (key -> cell)
		for (idx <- indexes) idx.register(cell)
		cell
	}

	/** Fetches the item with the requested key or creates it if it does not exist */
	def get(key: K): Rx[V] = items.get(key) match {
		case Some(cell) => cell
		case None => constructCell(key, default(key))
	}

	/** Update the value of an item in the cache */
	def update(item: V): Unit = Rx.atomically {
		val key = hash(item)
		items.get(key) match {
			case Some(cell) => cell := item
			case None => constructCell(key, item)
		}
	}

	/** Removes an item by key from the cache */
	def removeKey(key: K): Unit = Rx.atomically {
		items.get(key) match {
			case None => // Ignore
			case Some(cell) =>
				for (idx <- indexes) idx.unregister(cell)
				Rx.invalidate(cell)
				items -= key
		}
	}

	/** Removes an item by value from the cache */
	def remove(item: V): Unit = removeKey(hash(item))

	/** Clears the cache */
	def clear(): Unit = Rx.atomically {
		for (idx <- indexes) idx.clear()
		items.values.foreach(Rx.kill)
		items = items.empty
	}

	def contains(key: K): Boolean = items.contains(key)

	/**
	  * Base trait for indexes.
	  */
	abstract class BaseIndex {
		indexes += this

		private[Cache] def register(rx: Rx[V]): Unit = {}
		private[Cache] def unregister(rx: Rx[V]): Unit = {}
		private[Cache] def clear(): Unit = {}
	}

	/**
	  * Base trait for Indexes that uses an hash function.
	  *
	  * @tparam H the type of the hash
	  */
	trait HashingIndex[H] extends BaseIndex {
		private var observers = Map[Rx[V], Obs]()

		protected val hash: V => H
		protected def remove(hash: H, rx: Rx[V]): Unit
		protected def insert(hash: H, rx: Rx[V]): Unit

		protected def observerFor(currentHash: H, rx: Rx[V]): Obs = new Obs {
			private val previousHash: H = currentHash
			protected def callback(): Unit = {
				val newHash = hash(rx.!)
				if (previousHash != newHash) {
					remove(previousHash, rx)
					insert(newHash, rx)
				}
			}
		}

		private[Cache] override def register(rx: Rx[V]): Unit = {
			super.register(rx)
			val currentHash = hash(rx.!)
			val obs = observerFor(currentHash, rx)
			observers += (rx -> obs)
			rx ~> obs
			insert(currentHash, rx)
		}

		private[Cache] override def unregister(rx: Rx[V]): Unit = {
			super.unregister(rx)
			val obs = observers(rx)
			rx ~/> obs
			remove(hash(rx), rx)
			observers -= rx
		}

		private[Cache] override def clear(): Unit = {
			super.clear()
			for ((rx, obs) <- observers) rx ~/> obs
			observers = observers.empty
		}
	}

	/**
	  * TODO
	  *
	  * @param hash
	  * @tparam H
	  */
	class SimpleIndex[H: Ordering](protected val hash: V => H) extends BaseIndex with HashingIndex[H] {
		private val tree = Var(TreeMap[H, Var[Set[Rx[V]]]]())

		def get(key: H): Rx[Set[V]] = tree ~ (_.get(key)) ~ (_.map(_.!).getOrElse(Set.empty)) ~ (_.map(_.!))
		def from(lo: H): Rx[Set[V]] = tree ~ (_.from(lo).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def until(lo: H): Rx[Set[V]] = tree ~ (_.until(lo).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def range(lo: H, hi: H): Rx[Set[V]] = tree ~ (_.range(lo, hi).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))

		protected def remove(hash: H, rx: Rx[V]): Unit = {
			val set = tree.!(hash)
			set ~= (_ - rx)
			if (set.isEmpty) tree ~= (_ - hash)
		}

		protected def insert(hash: H, rx: Rx[V]): Unit = {
			tree.get(hash) match {
				case Some(set) => set ~= (_ + rx)
				case None => tree ~= (_ + (hash -> Var(Set(rx))))
			}
		}

		private[Cache] override def clear(): Unit = {
			super.clear()
			tree := tree.!.empty
		}
	}

	/**
	  * TODO
	  *
	  * @param hash
	  * @tparam H
	  */
	class RangeIndex[H: Ordering](protected val hash: V => (H, H)) extends BaseIndex with HashingIndex[(H, H)] {
		private val tree = Var(IntervalTree[H, Var[Set[Rx[V]]]]())

		def overlapping(lo: H, up: H): Rx[Set[V]] = tree ~ (_.overlapping(lo, up)) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def containing(lo: H, up: H): Rx[Set[V]] = tree ~ (_.containing(lo, up)) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def contained(lo: H, up: H): Rx[Set[V]] = tree ~ (_.contained(lo, up)) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))

		protected def remove(hash: (H, H), rx: Rx[V]): Unit = {
			val (lo, up) = hash
			val set = tree.!(lo, up)
			set ~= (_ - rx)
			if (set.isEmpty) tree ~= (_.remove(lo, up))
		}

		protected def insert(hash: (H, H), rx: Rx[V]): Unit = {
			val (lo, up) = hash
			tree.get(lo, up) match {
				case Some(set) => set ~= (_ + rx)
				case None => tree ~= (_.insert(lo, up, Var(Set(rx))))
			}
		}

		private[Cache] override def clear(): Unit = {
			super.clear()
			tree := tree.!.empty
		}
	}
}
