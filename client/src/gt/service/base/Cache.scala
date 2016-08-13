package gt.service.base

import scala.collection.immutable.TreeMap
import xuen.rx.{Obs, Rx, Var}

abstract class Cache[K, V <: AnyRef](hash: V => K) {
	/** Default value for newly allocated cells */
	def default(key: K): V = throw new UnsupportedOperationException("Default value factory is not defined")

	/** Collection of items in the cache */
	private var items = Map[K, Var[V]]()
	private var indexes = Set[Index[_]]()

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
	  * TODO
	  *
	  * @param hash
	  * @tparam H
	  */
	class Index[H: Ordering](hash: V => H) {
		protected val tree = Var(TreeMap[H, Var[Set[Rx[V]]]]())
		private var observers = Map[Rx[V], Obs]()

		items.values.foreach(register)
		indexes += this

		def get(key: H): Rx[Set[V]] = {
			tree ~ (_.get(key)) ~ (_.map(_.!).getOrElse(Set.empty)) ~ (_.map(_.!))
		}

		private def observerFor(currentHash: H, rx: Rx[V]): Obs = new Obs {
			private val previousHash: H = currentHash
			protected def callback(): Unit = {
				val newHash = hash(rx.!)
				if (previousHash != newHash) {
					remove(previousHash, rx)
					insert(newHash, rx)
				}
			}
		}

		private def remove(hash: H, rx: Rx[V]): Unit = {
			val set = tree.!(hash)
			set ~= (_ - rx)
			if (set.isEmpty) tree ~= (_ - hash)
		}

		private def insert(hash: H, rx: Rx[V]): Unit = {
			tree.get(hash) match {
				case Some(set) => set ~= (_ + rx)
				case None => tree ~= (_ + (hash -> Var(Set(rx))))
			}
		}

		private[Cache] def register(rx: Rx[V]): Unit = {
			val currentHash = hash(rx.!)
			val obs = observerFor(currentHash, rx)
			observers += (rx -> obs)
			rx ~> obs
			insert(currentHash, rx)
		}

		private[Cache] def unregister(rx: Rx[V]): Unit = {
			val obs = observers(rx)
			rx ~/> obs
			remove(hash(rx), rx)
			observers -= rx
		}

		private[Cache] def clear(): Unit = {
			for ((rx, obs) <- observers) {
				rx ~/> obs
			}
			tree := tree.!.empty
			observers = observers.empty
		}
	}
}
