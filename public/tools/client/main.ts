import { Socket, SocketDelegate } from "gtp3/socket";
import { EventEmitter } from "utils/eventemitter";
import { HelloFrame, HandshakeFrame, Frame } from "gtp3/frames";

declare var sock: any;

class Delegate {
	static socket: Socket = null;

	static connected() {
		console.log("connected");
		Delegate.socket.openChannel("$GUILDTOOLS", null);
	}

	static disconnected() { console.log("disconnected", arguments); }
	static reconnecting() { console.log("reconnecting", arguments); }
	static reset() { console.log("reset", arguments); }
	static updateLatency() { console.log("updateLatency", arguments); }
	static openChannel() { console.log("openChannel", arguments); }
}

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

	sock = new Socket("ws://localhost:9000/gtp3", Delegate);
	sock.connect();
}

export = main;
