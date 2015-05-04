import { Socket } from "core/gtp3";
import EventEmitter from "utils/eventemitter";

function main() {
	/*Pace.once("hide", function() {
		load();

		Pace.on("start", function() {
			$("#app-menu-icon").css("opacity", 0.3);
		});

		Pace.on("hide", function() {
			$("#app-menu-icon").css("opacity", 1);
		});
	});*/

	console.log("Hello world");

	var sock = new Socket("ws://localhost:9000/gtp3");
	sock._eventemitter_debug = true;
	sock.connect();
}

export = main;
