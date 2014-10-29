var $$ = null;

_(function() {
	if (window.navigator.standalone) {
		_("body").addClass("standalone");
	}
});

try {
	(function() {
		if (typeof require !== "function") return;

		var remote = require("remote");
		var ipc = require("ipc");

		var shell_version = ipc.sendSync("shell_version");
		if (shell_version !== "0.0.0") {
			$.error(
				"Update available",
				"An update of the GuildTools app is required to continue. It can be downloaded from the web version of GuildTools.",
					"shell_version: " + shell_version
			);
		}

		$$ = {
			version: shell_version
		};

		$$.win = remote.getCurrentWindow();

//
// Common
//
		$$.init = function() {
			var win = $$.win;

			// Show the window
			win.show();

			// Bindings when DOM is ready
			_(function() {
				var body = _("body");

				// Page is in app mode
				body.addClass("app").removeClass("live");

				// Bind window buttons
				_("#window-controls .close").click(function() {
					win.close();
				});

				_("#window-controls .maximize").click(function() {
					win.unmaximize();
					win.setFullScreen(!win.isFullScreen());
					_("#window-controls .maximize i").attr("class", win.isFullScreen() ? "awe-resize-small" : "awe-resize-full");
				});

				_("#window-controls .minify").click(function() {
					win.minimize();
				});
			});
		};

		$$.error = function() {
			_("#left-controls, #right-controls, #window-controls .minify, #window-controls .maximize, #breadcrumb").remove();
			_("header").hide().css({
				"background": "transparent",
				"border": "none",
				"z-index": 3005
			});
			setTimeout(function() {
				_("header").fadeIn();
			}, 100);
		};

	})();
} catch (e) {
}
