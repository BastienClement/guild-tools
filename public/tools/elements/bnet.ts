import { Element, Property, Dependencies, PolymerElement } from "elements/polymer";
import { Char } from "services/roster";

@Element("bnet-thumb", "/assets/imports/bnet.html")
export class BnetThumb extends PolymerElement {
	@Property(Object)
	public char: Char;

	@Property({ computed: "char" })
	private get src(): string {
		let char = this.char;
		let alt = `wow/static/images/2d/avatar/${char.race}-${char.gender}.jpg`;
		if (!char.thumbnail.match(/\.jpg$/))
			return `http://eu.battle.net/${alt}`;
		return `http://eu.battle.net/static-render/eu/${char.thumbnail}?alt=${alt}`;
	}
}
