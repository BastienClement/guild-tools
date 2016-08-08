package gt.component.misc

import gt.component.widget.GtBox
import gt.component.widget.form.{GtButton, GtCheckbox, GtLabel}
import gt.component.{GtHandler, Tab, View}
import util.Settings
import util.Settings.Setting
import util.annotation.data
import util.jsannotation.js
import xuen.Component
import xuen.rx.Var

object GtSettings extends Component[GtSettings](
	selector = "gt-settings",
	templateUrl = "/assets/imports/views/settings.html",
	dependencies = Seq(GtBox, GtButton, GtLabel, GtCheckbox)
) with View {
	val module = "settings"
	val tabs: TabGenerator = (selector, path, user) => Seq(
		Tab("Settings", "/settings", active = true)
	)
}

@js class GtSettings extends GtHandler {
	@data case class SettingsTab(title: String, icon: String, id: String)

	val tabs = Seq(
		//SettingsTab("General", "settings", "general"),
		//SettingsTab("Notifications", "notifications", "notifications"),
		//SettingsTab("Game", "folder", "game"),
		SettingsTab("Advanced", "build", "advanced")
	)

	val tab = Var[String]("advanced")

	val loadingFast = binding(Settings.`loading.fast`)
	val socketVerbose = binding(Settings.`socket.verbose`)

	def binding(setting: Setting[Boolean]): Var[Boolean] = {
		val rx = Var[Boolean](setting.value)
		rx ~> { state => setting := state }
		rx
	}
}
