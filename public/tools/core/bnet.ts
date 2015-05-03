import EventEmitter from "utils/eventemitter";

// The B.net callback function interface
interface BnetCallback {
	(data: any): void;
}

// An item of the B.net queue
interface QueueElement {
	api: string;
	cb: BnetCallback;
}

// Declaration of the global callback for Jsonps
declare var _bn_handle: Function;

/**
 * The B.net query manager
 */
class BnetManager extends EventEmitter {
	private queue: QueueElement[];
	private busy: boolean;

	private head: HTMLHeadElement;
	private script: HTMLScriptElement;
	private timeout: number;

	constructor() {
		super();
		this.queue = [];
		this.busy = false;
		this.head = document.getElementsByTagName("head")[0];
		this.script = null;
		this.timeout = -1;
	}

	query(api: string, cb: BnetCallback) {
		const call = { api: api, cb: cb };
		this.queue.push(call);
		this.emit("call-queued", call);
		//if (!Pace.running) Pace.restart();
		if (!this.busy) this.drain();
	}

	handle(data: any) {
		clearTimeout(this.timeout);

		const call = this.queue.shift();
		try {
			call.cb(data);
		} catch(e) {}

		this.emit("call-processed", call);

		if (this.queue.length) {
			this.drain();
		} else {
			this.emit("queue-drained");
			this.busy = false;
		}
	}

	private drain() {
		this.busy = true;
		var call = this.queue[0];
		var api = call.api;

		if (this.script) {
			this.head.removeChild(this.script);
		}

		var separator = (api.indexOf("?") === -1) ? "?" : "&";

		this.script = document.createElement("script");
		this.script.onerror = () => this.handle(null);
		this.script.onload = () => this.timeout = setTimeout(() => this.handle(null), 500);

		this.script.src = "//eu.battle.net/api/wow/" + api + separator + "jsonp=_bn_handle";
		this.head.appendChild(this.script);
	}

	inflight() {
		return this.queue.length;
	}
}

const Bnet = new BnetManager();

_pace_bn_get = () => Math.round(100 / (Bnet.inflight() + 1));
_bn_handle = Bnet.handle;

export default Bnet;
