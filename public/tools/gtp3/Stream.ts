import {EventEmitter} from "../utils/EventEmitter";
import {Channel} from "./Channel";

/**
 * Data stream over message channel implementation
 */
export class Stream extends EventEmitter {
	constructor(private channel: Channel) {
		super();
	}
}

export default Stream;
