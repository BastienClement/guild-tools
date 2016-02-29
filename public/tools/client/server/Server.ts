import {Component} from "../../utils/DI";
import {EventEmitter} from "../../utils/EventEmitter";
import {Socket} from "../../gtp3/Socket";
import {Channel} from "../../gtp3/Channel";
import {Notify} from "../../utils/Service";
import {ServiceChannel} from "./ServiceChannel";

/**
 * Interface to the GuildTools server
 */
@Component
export class Server extends EventEmitter {
	/**
	 * The underlying GTP3 socket object
	 */
	private socket: Socket;

	/**
	 * Deferred used for the initialization sequence
	 */
	private connect_deferred: PromiseResolver<void>;

	/**
	 * Server versions string
	 */
	public version: string = null;

	/**
	 * Track loading state
	 */
	@Notify
	public loading: boolean = false;

	/**
	 * Number of in-flight requests
	 */
	private request_count: number = 0;

	/**
	 * The loading indicator is currently being faded-in or out
	 */
	private loading_transition = false;

	/**
	 * Boostrap the server connection
	 * @param url               The server URL (with placeholders)
	 * @returns {Promise<void>} A promise that will be resolved when connected
	 */
	connect(url: string): Promise<void> {
		let defer = this.connect_deferred = Promise.defer<void>();
		let socket = this.socket = new Socket(this.normalizeURL(url));

		socket.verbose = (localStorage.getItem("socket.verbose") === "1");
		socket.pipe(this);
		socket.bind(this, {
			"connected": "Connected",
			"reconnecting": "Reconnecting",
			"disconnected": "Disconnected",
			"reset": "Reset",
			"channel-request": "ChannelRequest",
			"request-start": "RequestStart",
			"request-end": "RequestEnd"
		});

		socket.connect();
		return defer.promise;
	}

	/**
	 * Transform placeholder token in the WS host
	 * Replace $hostname, $port, $host by corresponding values from the location object.
	 * @param url           The URL with placeholders
	 * @returns {string}    The URL without placeholders
	 */
	private normalizeURL(url: string): string {
		for (let key of ["hostname", "port", "host"]) {
			url = url.replace(`$${key}`, (<any> location)[key]);
		}
		return url;
	}

	/**
	 * EVENT: The socket is connected to the server
	 * @param version   The server version string
	 */
	private Connected(version: string) {
		// Check if the server was updated
		if (version && this.version && this.version != version) {
			this.emit("version-changed");
			this.socket.close();
			return;
		}

		this.version = version;

		if (this.connect_deferred) {
			this.connect_deferred.resolve();
			this.connect_deferred = null;
		}
	}

	/**
	 * EVENT: The socket is definitively closed
	 * @param code      The close code sent by the server
	 * @param reason    The reason provided for closing the socket
	 */
	private Disconnected(code: number, reason: string) {
		if (this.connect_deferred) {
			this.connect_deferred.reject(new Error(`[${code}] ${reason}`));
		}
	}

	/**
	 * EVENT: Connection with the server was re-established but the session was lost
	 */
	private Reset() {
		// There is no way to do that properly on GT6, we'll need to reload the whole app
		// Modular architecture is too complex and stateful design cannot be restored properly
		this.socket.close();
	}

	/**
	 * EVENT: Incomming channel request
	 */
	private ChannelRequest() {
		// Currently, only the server can initiate channels.
		// After all, it doesn't seem like this is required...
		throw new Error("Unimplemented");
	}

	/**
	 * EVENT: A request started
	 */
	public RequestStart() {
		this.request_count++;
		if (this.request_count > 0) {
			this.updateLoading(true);
		}
	}

	/**
	 * EVENT: A request is completed
	 */
	public RequestEnd() {
		this.request_count--;
		if (this.request_count < 1) {
			this.updateLoading(false);
		}
	}

	/**
	 * Update the loading status.
	 * Enforce a delay on state changes to prevent flickering
	 * @param state The new loading status
	 */
	private async updateLoading(state: boolean) {
		// Follow the transition lockout
		if (this.loading_transition) return
		this.loading_transition = true;

		// Update loading state
		this.loading = state;

		// Lockout
		await Promise.delay(state ? 1500 : 500);
		this.loading_transition = false;

		// Ensure that the loading state is still valid
		if (state && this.request_count < 1) this.updateLoading(false);
		else if (!state && this.request_count > 0) this.updateLoading(true);
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
	public ping(): void {
		this.socket.ping();
	}

	/**
	 * Request a channel from the server
	 * @param ctype                 The channel type
	 * @returns {Promise<Channel>}  The channel object
	 */
	public openChannel(ctype: string): Promise<Channel> {
		this.RequestStart();
		let promise = this.socket.openChannel(ctype);
		return promise.finally(() => this.RequestEnd());
	}

	/**
	 * Open a service channel
	 * @param ctype                 The channel type
	 * @param lazy                  If set to false, the socket will be opened immediately.
	 *                              Otherwise, the channel will be lazily created on first request.
	 * @returns {ServiceChannel}    The service channel object
	 */
	public openServiceChannel(ctype: string, lazy: boolean = true): ServiceChannel {
		return new ServiceChannel(this, ctype, lazy);
	}

	/**
	 * Close the server connection
	 */
	public disconnect(): void {
		this.socket && this.socket.close();
	}
}
