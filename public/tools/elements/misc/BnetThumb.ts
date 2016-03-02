import {Element, Property} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {Char} from "../../services/roster/RosterService";

@Element({
	selector: "bnet-thumb",
	template: "/assets/imports/bnet.html"
})
export class BnetThumb extends PolymerElement {
	@Property(Object)
	public char: Char;

	@Property({computed: "char"})
	private get src(): string {
		let char = this.char;
		if (!char) return;
		let alt = `wow/static/images/2d/avatar/${char.race}-${char.gender}.jpg`;
		if (!char.thumbnail.match(/\.jpg$/))
			return `http://eu.battle.net/${alt}`;
		return `https://render-api-eu.worldofwarcraft.com/static-render/eu/${char.thumbnail}?alt=${alt}`;
	}
}
