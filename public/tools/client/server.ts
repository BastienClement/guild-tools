import { Component, Injector } from "utils/di";
import { Socket, SocketDelegate } from "gtp3/socket";
import { Channel } from "gtp3/channel";
import { Deferred } from "utils/deferred";
import { Queue } from "utils/queue";
import { EventEmitter } from "utils/eventemitter";
import { Service } from "utils/service";

export interface UserInformations {
	id: number;
	name: string;
	group: number;
	color: string;
}

@Component
export class Server extends EventEmitter {
	// The underlying socket object
	private socket: Socket;

	// Deferred used for the initialization sequence
	private connect_deferred: Deferred<void>;

	// Server versions string
	private version: string = null;

	// Information about the current user
	public user: UserInformations = null;

	// Boostrap the server connection
	connect(url: string): Promise<void> {
		const defer = this.connect_deferred = new Deferred<void>();
		const socket = this.socket = new Socket(this.normalizeURL(url));

		socket.verbose = true;
		socket.pipe(this);
		socket.bind(this, "connected", "reconnecting", "disconnected", "reset", "channel-request");
		socket.connect();

		return defer.promise;
	}
	
	// Transform placeholder token in the WS host
	private normalizeURL(url: string): string {
		for (let key of ["hostname", "port", "host"]) {
			url = url.replace(`$${key}`, (<any> location)[key]);
		}
		return url;
	}

	// The socket is connected to the server
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

	// Connection to the server was interrupted
	private "reconnecting"() {
		/**** FIXME ****/
		//status("Reconnecting...", true);
	}

	// The socket is definitively closed
	private "disconnected" (code: number, reason: string) {
		if (this.connect_deferred) {
			this.connect_deferred.reject(new Error(`[${code}] ${reason}`));
		}

		/**** FIXME ****/
		//error("Disconnected", "You were disconnected from the server.");
	}

	// Connection with the server was re-established but the session was lost
	private "reset"() {
		// There is no way to do that properly on GT6, we'll need to reload the whole app
	}

	// Incomming channel request
	private "channel-request"() {
		// Todo
		throw new Error("Unimplemented")
	}

	// Socket latency
	get latency(): number {
		return this.socket ? this.socket.latency : 0;
	}

	// Mesure the latency with the server
	public ping(): void {
		this.socket.ping();
	}

	// Request a channel from the server
	public openChannel(ctype: string): Promise<Channel> {
		return this.socket.openChannel(ctype);
	}
	
	// Open a service channel
	public openServiceChannel(ctype: string): ServiceChannel {
		return new ServiceChannel(this, ctype);
	}

	// Close the server connection
	public disconnect(): void {
		this.socket && this.socket.close();
	}
}

interface QueuedMessage<T> {
	key: string;
	data: any;
	flags: number;
	deferred: Deferred<T>
}

export type DispatchHandler = (payload: any) => void;
export type DispatchTable = Map<string, Map<string, DispatchHandler>>;

export class ServiceChannel extends EventEmitter {
	// Outgoing queue
	private queue = new Queue<QueuedMessage<any>>();
	
	// Channel status
	private first_open: boolean = true;
	private open_pending: boolean = false;
	private closed: boolean = true;
	
	// Channel object
	private channel: Channel;
	
	constructor(private server: Server, private name: string, lazy: boolean = false) {
		super();
		if (!lazy) {
			this.open();
		}
	}
	
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
					this.channel.request(item.key, item.data, item.flags).then(
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
	
	public close(code?: number, reason?: string) {
		this.closed = true;
		if (this.channel)
			this.channel.close(code, reason);
	}
	
	public request<T>(key: string, data?: any, flags?: number): Promise<T> {
		if (this.channel) {
			return this.channel.request<T>(key, data, flags);
		} else {
			const deferred = new Deferred<T>();
			this.queue.enqueue({ key, data, flags, deferred });
			this.open();
			return deferred.promise;
		}
	}
	
	public send(key: string, data?: any, flags?: number) {
		if (this.channel) {
			return this.channel.send(key, data, flags);
		} else {
			this.queue.enqueue({ key, data, flags, deferred: null });
			this.open();
		}
	}

	static Dispatch(source: string, message: string, splat?: boolean) {
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
		}
	}
	
	static ReflectState(source: string) {
		return (target: Service, property: string) => {
			let list = Reflect.getMetadata<[string, string][]>("servicechannel:state", target);
			if (!list) {
				list = [];
				Reflect.defineMetadata("servicechannel:state", list, target);
			}
			list.push([source, property]);
		}
	}
}
