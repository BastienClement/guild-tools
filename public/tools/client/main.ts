import { Socket } from "gtp3/socket";
import { EventEmitter } from "utils/eventemitter";
import { HelloFrame, HandshakeFrame, Frame } from "gtp3/frames";

declare var sock: any;

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

	sock = new Socket("ws://localhost:9000/gtp3");
	sock._eventemitter_debug = true;
	sock.connect();
}

export = main;
