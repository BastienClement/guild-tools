import {Provider, Inject, On, Property} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {StreamsService, ActiveStream} from "./StreamService";

/**
 * Provides the list of active streams
 */
@Provider("streams-list")
class StreamsListProvider extends PolymerElement {
	@Inject
	@On({
		"list-update": "ListUpdate",
		"notify": "Notify",
		"offline": "Offline"
	})
	private service: StreamsService;

	@Property({ notify: true })
	public list: ActiveStream[] = this.service.getStreamsList();

	private ListUpdate() {
		this.list = this.service.getStreamsList();
	}

	private Notify(stream: ActiveStream, new_stream: Boolean, idx: number) {
		if (new_stream) this.push("list", stream);
		else this.set(`list.${idx}`, stream);
	}

	private Offline(user: number, idx: number) {
		this.splice(`list`, idx, 1);
	}
}
