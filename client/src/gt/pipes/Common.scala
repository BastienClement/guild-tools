package gt.pipes

import xuen.expr.PipesCollection

object Common extends PipesCollection {
	declare("uppercase", (value: String) => value.toUpperCase)
	declare("lowercase", (value: String) => value.toLowerCase)
}
