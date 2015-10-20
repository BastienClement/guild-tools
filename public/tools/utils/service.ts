import { EventEmitter } from "utils/eventemitter";
import { DispatchTable } from "client/server";
import { defer } from "utils/deferred";

/**
 * An event emitter with pause() and resume() methods, automatically called
 * when no more event view are listening.
 */
export class Service extends EventEmitter {
	// Attached objects
	private attachedListeners = new WeakSet<Object>();
	private attachedCount = 0;
	private attachedState = true;
	
	constructor() {
		super();
        const that = <any>this;
		
        defer(() => {
            const table = Reflect.getMetadata<DispatchTable>("servicechannel:dispatch", this);
            if (table) {
                table.forEach((mapping, source) => {
                    that[source].on("message", (msg: string, payload: any) => {
                        const handler = mapping.get(msg);
                        if (handler) {
                            handler.call(this, payload);
                        } else {
                            console.error("Unknown message:", msg, payload);
                        }
                    });
                });
            }

            const state = Reflect.getMetadata<[string, string][]>("servicechannel:state", this);
            if (state) {
                for (let [source, target] of state) {
                    that[source].on("state", (s: boolean) => that[target] = s);
                }
            }
        });
	}

	/**
	 * Attach an object to this emitter
	 * Call resume() if defined and pause() was previously called
	 */
	public attachListener(listener: Object) {
		if (!this.attachedListeners.has(listener)) {
			this.attachedListeners.add(listener);
			this.attachedCount += 1;
			if (!this.attachedState) {
				this.attachedState = true;
				const that: any = this;
				if (that.resume) that.resume();
			}
		}
	}

	/**
	 * Detache an object from this emitter
	 * If the count of attached is now 0, call pause()
	 */
	public detachListener(listener: Object) {
		if (this.attachedListeners.has(listener)) {
			this.attachedListeners.delete(listener);
			this.attachedCount -= 1;
			if (this.attachedCount == 0 && this.attachedState) {
				this.attachedState = false;
				const that: any = this;
				if (that.pause) that.pause();
			}
		}
    }
}

/**
 * Automatically emits event when a property is updated
 */
export function Notify(target: EventEmitter, property: string) {
	const notify = Reflect.getMetadata<{ [prop: string]: boolean }>("eventemitter:notify", target) || {};
	notify[property] = true;
	Reflect.defineMetadata("eventemitter:notify", notify, target);
}
