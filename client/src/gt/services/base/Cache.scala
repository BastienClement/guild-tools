package gt.services.base

import gt.services.base.CacheUtils.LiftedHashFunction
import rx.{Obs, Rx, Var}
import scala.collection.immutable.TreeMap
import utils.intervaltree.IntervalTree

/**
  * A reactive local data cache.
  *
  * Caches are used to store data fetched from the server.
  *
  * The task of maintaining an up-to-date reactive representation of the data is
  * tedious. Caches provides a was to automatically store, index, update and retrieve
  * values as well as constructing default value when some data is missing.
  *
  * The main hash function is used to compute the primary indexing key. Only
  * a single element can be stored for a given primary key. If another value with
  * the same primary key is inserted, its values will overwrite the previous value.
  *
  * Caches can define additional indexes based on arbitrary criteria for faster access.
  *
  * @param hash the main index hash function
  * @tparam K the type of the main index key
  * @tparam V the type of values stored in the cache
  */
abstract class Cache[K, V <: AnyRef](hash: V => K) {
	/**
	  * Default value factory for newly allocated cells.
	  *
	  * The default implementation is undefined and instead throws an
	  * exception. The intent here is that concrete instances of a cache
	  * overrides this method to provide a appropriate default value for
	  * the item and optionally initiate some computation to obtain the
	  * actual value that should be associated with the key.
	  *
	  * The item can then be updated by calling the `update` method.
	  */
	def default: V = throw new UnsupportedOperationException("Default value factory is not defined")

	/**
	  * Default value key-sensible factory for newly allocated cells.
	  *
	  * The default implementation delegate to the key-less `default` method.
	  * If there is no need for the key to be known in order to generate the
	  * default value, concrete classes should override the key-less version
	  * of this method. Overriding this overload allows to use the requested
	  * key as part of the computation of the default value.
	  *
	  * @param key the key for which the value is being generated
	  */
	def default(key: K): V = default

	/** Collection of items in the cache */
	private var items = Map[K, Var[V]]()

	/** Sets of defined index for this cache */
	private var indexes = Set[BaseIndex]()

	/**
	  * A reactive iterator over every values in this cache
	  */
	val values: Rx[Iterable[Rx[V]]] = Rx { items.values }

	/**
	  * Constructs a new cache cell for the given key and value.
	  *
	  * @param key   the computed key for this item
	  * @param value the current value of this item
	  */
	private def constructCell(key: K, value: V): Var[V] = {
		val cell = Var[V](value)
		items += (key -> cell)
		Rx.invalidate(values)
		for (idx <- indexes) idx.register(cell)
		cell
	}

	/**
	  * Fetches the item with the requested key or creates it if it does not exist.
	  *
	  * The `default` factory method will be invoked with the requested key to
	  * create the default value. Its default implementation is undefined and
	  * instead throws an exception. You must be sure that it is possible to
	  * construct default instances before requesting a key that is not available
	  * in the cache.
	  *
	  * @param key the key to look up for in the cache
	  */
	def get(key: K): Rx[V] = items.get(key) match {
		case Some(cell) => cell
		case None => constructCell(key, default(key))
	}

	def getOption(key: K): Rx[Option[V]] = Rx {
		if (contains(key)) Some(get(key).!)
		else None
	}

	/**
	  * Updates the value of an item in the cache.
	  *
	  * The item is considered a new version of a previously stored item if both
	  * hashes are the same. If the item is not yet present in the cache, it is
	  * instead inserted.
	  *
	  * @param item the updated item
	  */
	def update(item: V): Unit = Rx.atomically {
		val key = hash(item)
		items.get(key) match {
			case Some(cell) => cell := item
			case None => constructCell(key, item)
		}
	}

	/**
	  * Removes an item by key from the cache.
	  *
	  * @param key the key to remove from the cache
	  */
	def removeKey(key: K): Unit = Rx.atomically {
		items.get(key) match {
			case None => // Ignore
			case Some(cell) =>
				for (idx <- indexes) idx.unregister(cell)
				Rx.invalidate(cell)
				items -= key
				Rx.invalidate(values)
		}
	}

	/**
	  * Removes an item by value from the cache.
	  *
	  * This function recomputes the item's current key and is thus less
	  * efficient that the functionally equivalent `removeKey` if the key
	  * values is known.
	  *
	  * @param item the item to remove from the cache
	  */
	def remove(item: V): Unit = removeKey(hash(item))

	/**
	  * Removes every items in the cache matching the given predicate.
	  *
	  * @param predicate the predicate to use
	  */
	def prune(predicate: V => Boolean): Unit = Rx.atomically {
		for ((key, item) <- items if predicate(item.!)) removeKey(key)
	}

	/**
	  * Clears the cache, removing every entries.
	  */
	def clear(): Unit = Rx.atomically {
		for (idx <- indexes) idx.clear()
		items.values.foreach(Rx.kill)
		items = items.empty
		Rx.invalidate(values)
	}

	/**
	  * Checks if the caches contains the given key.
	  *
	  * @param key the key to search
	  */
	def contains(key: K): Boolean = items.contains(key)

	/**
	  * Base trait for indexes.
	  *
	  * All indexes are expected to require notification on addition
	  * or removal of values from the cache. This trait defines dummy
	  * defaults for these operations allowing the implementing classes
	  * to compose the actual behavior of the index by using the cake
	  * pattern from various traits.
	  */
	abstract class BaseIndex {
		// Registers this index in the cache registry
		indexes += this

		/** Called when a new entry is added to the cache */
		private[Cache] def register(rx: Rx[V]): Unit = ()

		/** Called when an entry is removed from the cache */
		private[Cache] def unregister(rx: Rx[V]): Unit = ()

		/** Called when the cache is cleared, in this case, `unregister` is not called */
		private[Cache] def clear(): Unit = ()
	}

	/**
	  * Base trait for Indexes that uses an hash function.
	  *
	  * Values will automatically be hashed and `insert` and `remove` functions
	  * will be called to accommodate from changes in cached values.
	  *
	  * @tparam H the type of the hash
	  */
	trait HashingIndex[H] extends BaseIndex {
		/** The interface of hash observer */
		trait HashObs extends Obs {
			def onRemove(): Unit
		}

		/** A local registry of observers associated with reactive values */
		private var observers = Map[Rx[V], HashObs]()

		/** The hash function to use */
		protected val hash: LiftedHashFunction[V, H]

		/** Called when an element is inserted or modified */
		protected def insert(hash: H, rx: Rx[V]): Unit

		/** Called when an element is removed or modified */
		protected def remove(hash: H, rx: Rx[V]): Unit

		/**
		  * Constructs the observer instance for a given reactive value.
		  *
		  * The observer will monitor changes of the reactive values and
		  * ensure that the index is keep in sync if the hash result for
		  * this item changes. There is no method `change`, instead `remove`
		  * and `insert` are called successively.
		  *
		  * The observer always keep a local snapshot of the previous hash
		  * value to be able to remove the previous entry even after the
		  * previous value is no longer accessible and thus hash can no
		  * longer be computed.
		  *
		  * @param rx          the reactive value
		  */
		protected def observerFor(rx: Rx[V]): HashObs = new HashObs {
			/** The previous hash value */
			private var previousHash: Option[H] = None

			/** Called when the reactive value changes */
			protected def callback(): Unit = {
				val newHash = hash(rx.!)
				(previousHash, newHash) match {
					case (Some(oldh), Some(newh)) if oldh != newh =>
						remove(oldh, rx)
						insert(newh, rx)
					case (Some(oldh), None) =>
						remove(oldh, rx)
					case (None, Some(newh)) =>
						insert(newh, rx)
					case _ => // ignore
				}
				previousHash = newHash
			}

			def onRemove(): Unit = for (h <- previousHash) remove(h, rx)
		}

		/**
		  * Registers the reactive value and its observable in the local registry.
		  *
		  * @param rx the reactive value that was just added to the cache
		  */
		private[Cache] override def register(rx: Rx[V]): Unit = {
			super.register(rx)
			val obs = observerFor(rx)
			observers += (rx -> obs)
			rx ~>> obs
		}

		/**
		  * Removes the reactive value and its observer from the local registry.
		  *
		  * @param rx the reactive value that was just removed to the cache
		  */
		private[Cache] override def unregister(rx: Rx[V]): Unit = {
			super.unregister(rx)
			val obs = observers(rx)
			rx ~/> obs
			obs.onRemove()
			observers -= rx
		}

		/**
		  * Clears every values and observers from the local registry.
		  */
		private[Cache] override def clear(): Unit = {
			super.clear()
			for ((rx, obs) <- observers) rx ~/> obs
			observers = observers.empty
		}
	}

	/**
	  * Simple index storing data in a tree.
	  *
	  * The hashing function must produce keys of type H for which an
	  * Ordering[H] is implicitly available.
	  *
	  * @param hash the hash function to use
	  * @tparam H the type of value produces by the hash function
	  */
	class SimpleIndex[H: Ordering](protected val hash: LiftedHashFunction[V, H]) extends BaseIndex with HashingIndex[H] {
		def this(hash: V => H) = this(LiftedHashFunction((v: V) => Some(hash(v))))
		def this(hash: PartialFunction[V, H]) = this(LiftedHashFunction(hash.lift))

		/** The data tree */
		private val tree = Var(TreeMap[H, Var[Set[Rx[V]]]]())

		/**
		  * Returns every items in the cache that matches the given key.
		  *
		  * @param key the key to lookup in the index
		  */
		def get(key: H): Rx[Set[V]] = tree ~ (_.get(key)) ~ (_.map(_.!).getOrElse(Set.empty)) ~ (_.map(_.!))

		/**
		  * Returns every items in the cache for which the computed key
		  * is higher than the given bound.
		  *
		  * @param lower the lower bound (inclusive)
		  */
		def from(lower: H): Rx[Set[V]] = {
			tree ~ (_.from(lower).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		}

		/**
		  * Returns every items in the cache for which the computed key
		  * is lower than the given bound.
		  *
		  * @param upper the upper bound (inclusive)
		  */
		def to(upper: H): Rx[Set[V]] = {
			tree ~ (_.to(upper).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		}

		/**
		  * Returns every items in the cache for which the computed key
		  * is lower than the given bound.
		  *
		  * @param upper the upper bound (exclusive)
		  */
		def until(upper: H): Rx[Set[V]] = {
			tree ~ (_.until(upper).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		}

		/**
		  * Returns every items in the cache for which the computed key
		  * is between the given bounds.
		  *
		  * @param lower the lower bound (inclusive)
		  * @param upper the upper bound (exclusive)
		  */
		def range(lower: H, upper: H): Rx[Set[V]] = {
			tree ~ (_.range(lower, upper).valuesIterator) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		}

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
	class RangeIndex[H: Ordering](protected val hash: LiftedHashFunction[V, (H, H)]) extends BaseIndex with HashingIndex[(H, H)] {
		def this(hash: V => (H, H)) = this(LiftedHashFunction((v: V) => Some(hash(v))))
		def this(hash: PartialFunction[V, (H, H)]) = this(LiftedHashFunction(hash.lift))

		private val tree = Var(IntervalTree[H, Var[Set[Rx[V]]]]())

		def overlapping(lo: H, up: H): Rx[Set[V]] = tree ~ (_.overlapping(lo, up)) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def contained(lo: H, up: H): Rx[Set[V]] = tree ~ (_.contained(lo, up)) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		def containing(lo: H, up: H): Rx[Set[V]] = tree ~ (_.containing(lo, up)) ~ (_.foldLeft(Set.empty[V])(_ ++ _.!.map(_.!)))
		@inline final def containing(lo: H): Rx[Set[V]] = containing(lo, lo)

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
