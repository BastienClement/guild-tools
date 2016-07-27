package gt.component

import gt.App
import util.jsannotation.js
import xuen.Handler

/**
  * Common additions to every component handler in GuildTools
  */
@js abstract class GtHandler extends Handler {
	final val app: App.type = App
}
