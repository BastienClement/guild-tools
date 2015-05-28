import { Component, Injector } from "utils/di";
import { Socket, SocketDelegate } from "gtp3/socket";
import { Deferred } from "utils/deferred";
import { EventEmitter } from "utils/eventemitter";
import { error, status } from "client/dialog";

@Component
export class Server extends EventEmitter {
	// The underlying socket object
	private socket: Socket;

	// Deferred used for the initialization sequence
	private connect_deferred: Deferred<void>;

	// Server versions string
	private version: string = null;

	/**
	 * Boostrap the server connection
	 */
	connect(url: string): Promise<void> {
		this.connect_deferred = new Deferred<void>();
		this.socket = new Socket(url);
		this.socket.verbose = true;
		this.socket.connect();

		this.socket.pipe(this);
		this.socket.bind(this, "connected", "reconnecting", "disconnected", "reset", "channel-request");

		return this.connect_deferred.promise;
	}

	private "connected" (version: string) {
		if (this.version && this.version != version) {
			error("Server updated", "test");
		}

		if (this.connect_deferred) {
			this.connect_deferred.resolve();
			this.connect_deferred = null;
		}
		
		status(null);
	}

	private "reconnecting"() {
		status("Reconnecting...", true);
	}

	private "disconnected" (code: number, reason: string) {
		if (this.connect_deferred) {
			this.connect_deferred.reject(new Error(`[${code}] ${reason}`));
		}
		
		error("Disconnected", "You were disconnected from the server.");
	}

	private "reset"() {
	}

	private "channel-request" () {
	}

	/**
	 * Access the socket latency
	 */
	get latency(): number {
		return this.socket ? this.socket.latency : 0;
	}
	
	/**
	 * Channel request
	 */
	openChannel(ctype: string) {
		return this.socket.openChannel(ctype);
	}
	
	/**
	 * Close the server connection
	 */
	disconnect() {
		this.socket.close();
	}
}
