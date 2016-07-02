import {Element, Inject, Property} from "../../polymer/Annotations";
import {GtAlert} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {StreamsService, ActiveStream} from "../../services/stream/StreamService";

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-player>

@Element({
	selector: "streams-player",
	template: "/assets/views/streams.html",
	dependencies: [GtAlert]
})
export class StreamsPlayer extends PolymerElement {
	@Inject
	private service: StreamsService;

	@Property({observer: "update"})
	public stream: ActiveStream;

	private player: HTMLIFrameElement = null;
	private error: String = null;

	private async removePlayer() {
		while (this.player) {
			this.player.remove();
			this.player = null;
			await Promise.delay(400);
		}
	}

	private async update() {
		this.error = null;
		await this.removePlayer();

		if (this.stream) {
			try {
				let [ticket, stream] = await this.service.requestTicket(this.stream.user);
				await this.removePlayer();
				let player = document.createElement("iframe");
				player.src = `/clappr/${stream}?${ticket}`;
				player.allowFullscreen = true;
				this.player = player;
				this.shadow.appendChild(player);
			} catch (e) {
				this.error = e.message;
			}
		}
	}
}
