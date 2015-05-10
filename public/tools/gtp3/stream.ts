import { EventEmitter } from "utils/eventemitter";
import { Channel } from "gtp3/channel";

/**
 * Data stream over message channel implementation
 */
export class Stream extends EventEmitter {
	constructor(private channel: Channel) {
		super();
	}
}

export default Stream;
