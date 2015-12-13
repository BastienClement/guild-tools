import play.libs.Akka
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{higherKinds, implicitConversions}

package object reactive {
	implicit lazy val ExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("default-pool")
	implicit lazy val DefaultDomain = new Domain

	/**
	  * Tranform a block to a future, but without executing is asynchronously.
	  *
	  * @param body The block to execute
	  * @tparam T Return type of the block
	  * @return The return value of the block
	  */
	def AsFuture[T](body: => T): Future[T] = {
		try {
			Future.successful(body)
		} catch {
			case e: Throwable => Future.failed(e)
		}
	}

	/**
	  * Alias for an Rx object type in some domain.
	  * @tparam T The value of the reactive value.
	  */
	type Rx[+T] = Domain#Rx[T]

	/**
	  * An RxToolkit provides domain-bound operations.
	  * @tparam A The type of Rxs of this domain
	  * @tparam B The type of Vars of this domain
	  * @tparam C The type of Obs of this domain
	  */
	trait RxToolkit[A[_] <: Domain#Rx[_], B[_] <: Domain#Var[_], C <: Domain#Obs] {
		protected type R[T] = A[T]
		protected type V[T] = B[T]
		protected type O = C

		/**
		  * Constructs a new Rx value from an arbitrary expression.
		  *
		  * @param gen The generator expression
		  * @tparam T The type of returned values
		  * @return A new OpaqueRx object enclosing this generator
		  */
		def apply[T](gen: => T): R[T]

		/**
		  * Constructs a new reactive variable.
		  *
		  * @param initial The initial value of the variable
		  * @tparam T The type of the variable
		  * @return A new reactive variable
		  */
		def variable[T](initial: T): V[T]

		/**
		  * Constructs a new observer.
		  * The observer handler will be executed when one of the observed values changes.
		  *
		  * @param skipInitial If true, the observer will not be notifed until after the observed value changes
		  *                    fot the first time.
		  * @param rx The Rx to observe
		  * @param rxs List of other Rxs to observe
		  * @param handler The handler that will be executed when one of the Rx changes
		  * @return The constructed observer
		  */
		def observe(skipInitial: Boolean = false)(rx: R[_], rxs: R[_]*)(handler: => Unit): O = observe(rx, rxs, handler, skipInitial)

		/**
		  * Constructs a new observer.
		  * The observer handler will be executed when one of the observed values changes.
		  *
		  * @param rx The Rx to observe
		  * @param rxs List of other Rxs to observe
		  * @param handler The handler that will be executed when one of the Rx changes
		  * @return The constructed observer
		  */
		def observe(rx: R[_], rxs: R[_]*)(handler: => Unit): O = observe(rx, rxs, handler, false)

		/** Internal common constructor */
		protected def observe(rx: R[_], rxs: Seq[R[_]], handler: => Unit, skipInitial: Boolean): O

		/**
		  * Construct a new Rx that will produce every values produces by merged Rxs.
		  *
		  * @param a The first Rx to merge
		  * @param b The second Rx to merge
		  * @param tail Additional Rxs to merge
		  * @tparam U The common type of the Rxs
		  */
		def merge[U](a: R[U], b: R[U], tail: R[U]*): R[U]
	}

	/**
	  * Constructs an RxToolkit bound to an implicitly given domain.
	  */
	def Rx(implicit domain: Domain): RxToolkit[domain.Rx, domain.Var, domain.Obs] =
		new RxToolkit[domain.Rx, domain.Var, domain.Obs] {
			def apply[T](gen: => T): R[T] = new domain.OpaqueRx[T](() => gen)
			def variable[T](initial: T): V[T] = new domain.Var(initial)
			def observe(rx: R[_], rxs: Seq[R[_]], handler: => Unit, skipInitial: Boolean): domain.Obs = {
				val targets = rx +: rxs
				val observer = targets.foldLeft(new domain.Obs(() => handler))(_.bindTo(_))
				if (skipInitial) for (rx <- targets) rx()
				else observer.trigger()
				observer
			}
			def merge[U](a: R[U], b: R[U], tail: R[U]*): R[U] = a.merge(b, tail: _*)
		}

	/**
	  * Construct a new reactive value from an arbitrary expression.
	  *
	  * @param gen The generator expression used to compute the value
	  * @param domain The domain that will own this Rx
	  * @tparam T Type of the reactive value
	  * @return A new OpaqueRx object enclosing this generator
	  */
	def Rx[T](gen: => T)(implicit domain: Domain): domain.Rx[T] = new domain.OpaqueRx[T](() => gen)

	/**
	  * Alias for a Var object type in some domain.
	  * @tparam T The type of the variable
	  */
	type Var[T] = Domain#Var[T]
	def Var[T](initial: T)(implicit domain: Domain): domain.Var[T] = new domain.Var[T](initial)

	/**
	  * Alias for an Obs object type in some domain.
	  */
	type Obs = Domain#Obs

	/**
	  * Atomically executes an block on this domain.
	  * During the block execution, no observer will be notified of invalidations
	  * occuring in the domain. Once the block execution completes, every pending
	  * observers will be notified in a topologically-based order.
	  *
	  * @param block The block to execute
	  * @param domain The domain that will be used for synchronization
	  * @tparam T The return type of the block
	  * @return The return value of the block
	  */
	def Atomic[T](block: => T)(implicit domain: Domain): T = domain.atomically[T](block)
}
