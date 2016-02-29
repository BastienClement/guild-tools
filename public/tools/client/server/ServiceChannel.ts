import {EventEmitter} from "../../utils/EventEmitter";
import {Queue} from "../../utils/Queue";
import {Channel} from "../../gtp3/Channel";
import {Server} from "./Server";
import {Service} from "../../utils/Service";

/**
 * A queued outgoing message.
 * If a ServiceChannel is closed when calling request() or send(), open() will be automatically called.
 * The message is then saved in a queue until the channel is ready for sending it.
 */
type QueuedMessage<T> = {
	key: string;
	data: any;
	flags: number;
	silent: boolean;
	deferred: PromiseResolver<T>
}

/**
 * Interface of a function that can be decorated by ServiceChannel.Dispatch
 */
export type DispatchHandler = (payload: any) => void;

/**
 * Interface of the table used by ServiceChannel.Dispatch.
 * A single dispatch table is shared by every decorated function in an object.
 */
export type DispatchTable = Map<string, Map<string, DispatchHandler>>;

/**
 * A ServiceChannel
 * Like Channel, but automatically managed
 */
export class ServiceChannel extends EventEmitter {
	/**
	 * Outgoing queue of messages
	 */
	private queue = new Queue<QueuedMessage<any>>();

	/**
	 * Channel is open for the first time
	 */
	private first_open: boolean = true;

	/**
	 * The channel is currently being open
	 */
	private open_pending: boolean = false;

	/**
	 * The channel is closed
	 */
	private closed: boolean = true;

	/**
	 * The underlying Channel object
	 */
	private channel: Channel;

	/**
	 * Constructor
	 * @param server    The server object
	 * @param name      Name of this channel
	 * @param lazy      If lazy, the underlying channel will not be created until the first request or message
	 */
	constructor(private server: Server, private name: string, lazy: boolean) {
		super();
		if (!lazy) {
			this.open();
		}
	}

	/**
	 * Open the service channel.
	 * Called automatically when doing request or sending message on a closed channel.
	 */
	public async open() {
		if (this.channel || this.open_pending) return;

		this.closed = false;
		this.open_pending = true;

		try {
			// Open the actual channel
			const chan = await this.server.openChannel(this.name);
			this.channel = chan;

			// Emit a reset event if this is not the first time this channel is opened
			if (this.first_open) {
				this.first_open = false;
			} else {
				this.emit("reset");
			}

			this.emit("state", true);

			// Pipe every event to this emitter except reset and close
			chan.pipe(this, "!", "reset", "close");

			// Handle close
			chan.on("close", () => {
				this.channel = null;
				this.emit("state", false);
				if (this.closed) return;
				this.open();
			});

			// Flush queue
			while (!this.queue.empty()) {
				const item = this.queue.dequeue();
				if (item.deferred) {
					this.channel.request(item.key, item.data, item.flags, item.silent).then(
						(success) => item.deferred.resolve(success),
						(failure) => item.deferred.reject(failure)
					);
				} else {
					this.channel.send(item.key, item.data, item.flags);
				}
			}
		} catch (e) {
			throw new Error(`Failed to open ${this.name} channel: ${e}`);
		} finally {
			this.open_pending = false;
		}
	}

	/**
	 * Close the service channel.
	 * Unlink Channel.close, a service channel can be re-opened after being closed.
	 * @param code      Close code
	 * @param reason    Close reason
	 */
	public close(code?: number, reason?: string) {
		this.closed = true;
		if (this.channel)
			this.channel.close(code, reason);
	}

	/**
	 * Send a request on the service channel.
	 * Similar to Channel.request, but will open the service channel if lazy and not yet opened.
	 * @param key               Message type
	 * @param data              Message payload
	 * @param flags             Initial flags for the message
	 * @param silent            If silent, this request will not trigger the activity indicator
	 * @returns {Promise<T>}    The request result
	 */
	public request<T>(key: string, data?: any, flags?: number, silent?: boolean): Promise<T> {
		if (this.channel) {
			return this.channel.request<T>(key, data, flags, silent);
		} else {
			const deferred = Promise.defer<T>();
			this.queue.enqueue({ key, data, flags, silent, deferred });
			this.open();
			return deferred.promise;
		}
	}

	/**
	 * Send a message on the service channel.
	 * Similar to Channel.send, but will open the service channel if lazy and not yet opened.
	 * @param key   Message type
	 * @param data  Message payload
	 * @param flags Initial flags for the message
	 */
	public send(key: string, data?: any, flags?: number) {
		if (this.channel) {
			this.channel.send(key, data, flags);
		} else {
			this.queue.enqueue({ key, data, flags, silent: false, deferred: null });
			this.open();
		}
	}

	/**
	 * Construct a decorator function that will invoke the decorated function when
	 * a given message is received on the channel.
	 * @param source    The service channel property name
	 * @param message   The message type to catch
	 * @param splat     If true, the message content must be an array and this array will
	 *                  be used as arguments list to the function instead of the array itself.
	 */
	static Dispatch(source: string, message: string, splat: boolean = false) {
		return (target: Service, property: string) => {
			let table = Reflect.getMetadata<DispatchTable>("servicechannel:dispatch", target);
			if (!table) {
				table = new Map();
				Reflect.defineMetadata("servicechannel:dispatch", table, target);
			}

			let mapping = table.get(source);
			if (!mapping) {
				mapping = new Map();
				table.set(source, mapping);
			}

			const fn = <DispatchHandler> (<any> target)[property];
			mapping.set(message, splat ? function (p) { fn.apply(this, p); } : fn);
		};
	}

	/**
	 * Construct a decorator function that will update the decorated property with the
	 * current state of the service channel given as `source`.
	 * @param source    The service channel property name
	 */
	static ReflectState(source: string) {
		return (target: Service, property: string) => {
			let list = Reflect.getMetadata<[string, string][]>("servicechannel:state", target);
			if (!list) {
				list = [];
				Reflect.defineMetadata("servicechannel:state", list, target);
			}
			list.push([source, property]);
		};
	}
}
