package utils

object Bindings {
	def apply[S, T]() = new Bindings[S, T]
}

class Bindings[S, T] {
	private var source_target = Map[S, T]()
	private var target_source = Map[T, S]()

	def add(b: (S, T)) = {
		source_target += b
		target_source += b.swap
	}

	def containsSource(s: S) = source_target contains s
	def containsTarget(t: T) = target_source contains t

	def getSource(t: T) = target_source.get(t)
	def getTarget(s: S) = source_target.get(s)

	def removeSource(s: S) = for (t <- source_target.get(s)) remove(s, t)
	def removeTarget(t: T) = for (s <- target_source.get(t)) remove(s, t)

	private def remove(s: S, t: T) = {
		source_target -= s
		target_source -= t
	}

	def toMap = source_target
	def toReverseMap = target_source
}
