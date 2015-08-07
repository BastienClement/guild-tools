import { Component, Injector } from "utils/di";
import { Socket, SocketDelegate } from "gtp3/socket";
import { Deferred } from "utils/deferred";
import { EventEmitter } from "utils/eventemitter";

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
		const defer = this.connect_deferred = new Deferred<void>();
		const socket = this.socket = new Socket(url);

		socket.verbose = true;
		socket.pipe(this);
		socket.bind(this, "connected", "reconnecting", "disconnected", "reset", "channel-request");
		socket.connect();

		return defer.promise;
	}

	/**
	 * The socket is connected to the server
	 */
	private "connected"(version: string) {
		// Check if the server was updated
		if (this.version && this.version != version) {
			/**** FIXME ****/
			//error("Server updated", "test");
		}

		this.version = version;

		if (this.connect_deferred) {
			this.connect_deferred.resolve();
			this.connect_deferred = null;
		}

		/**** FIXME ****/
		//status(null);
	}

	/**
	 * Connection to the server was interrupted
	 */
	private "reconnecting"() {
		/**** FIXME ****/
		//status("Reconnecting...", true);
	}

	/**
	 * The socket is definitively closed
	 */
	private "disconnected" (code: number, reason: string) {
		if (this.connect_deferred) {
			this.connect_deferred.reject(new Error(`[${code}] ${reason}`));
		}

		/**** FIXME ****/
		//error("Disconnected", "You were disconnected from the server.");
	}

	/**
	 * Connection with the server was re-established but the session was lost
	 */
	private "reset"() {
		// There is no way to do that properly on GT6, we'll need to reload the whole app
	}

	/**
	 * Incomming channel request
	 */
	private "channel-request"() {
		// Todo
		throw new Error("Unimplemented")
	}

	/**
	 * Socket latency
	 */
	get latency(): number {
		return this.socket ? this.socket.latency : 0;
	}

	/**
	 * Mesure the latency with the server
	 */
	public ping() {
		this.socket.ping();
	}

	/**
	 * Request a channel from the server
	 */
	public openChannel(ctype: string) {
		return this.socket.openChannel(ctype);
	}

	/**
	 * Close the server connection
	 */
	public disconnect() {
		this.socket && this.socket.close();
	}
}
