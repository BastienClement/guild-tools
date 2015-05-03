import Bnet from "core/bnet";

function load() {

}

function init() {
	Pace.once("hide", function() {
		load();

		Pace.on("start", function() {
			$("#app-menu-icon").css("opacity", 0.3);
		});

		Pace.on("hide", function() {
			$("#app-menu-icon").css("opacity", 1);
		});
	});
}

export default init;
