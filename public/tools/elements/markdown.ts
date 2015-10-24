import { Element, Property, PolymerElement } from "elements/polymer";
import { defer } from "utils/async";
import { parse, Renderer } from "marked";

const renderer = new Renderer();

renderer.heading = (text, level) => (level > 2) ? `<p>${text}</p>` : `<h${level}>${text}</h${level}>`;

@Element("gt-markdown", "/assets/imports/markdown.html")
export class GtMarkdown extends PolymerElement {
	@Property({ observer: "update" })
	public src: string;

	public update() {
		this.$.markdown.innerHTML = parse(this.src, {
			renderer,
			gfm: true,
			tables: true,
			breaks: true,
			sanitize: true,
			smartypants: true
		});
		
		defer(() => this.fire("rendered"));
	}
}
