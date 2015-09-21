import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind, PolymerElement, PolymerModelEvent, PolymerMetadata } from "elements/polymer";
import moment from "moment";

@Element("gt-timeago")
export class GtTimeago extends PolymerElement {
	@Property({ observer: "update" })
	public date: string;

	private ticker: any;

	attached() {
		clearInterval(this.ticker);
		this.ticker = setInterval(() => this.update(), 60000);
		this.update();
	}

	detached() {
		clearInterval(this.ticker)
	}

	public update() {
		this.shadow.innerHTML = moment(this.date).fromNow()
	}
}
