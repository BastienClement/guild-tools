import {PolymerElement} from "../../polymer/PolymerElement";
import {Element, Inject, On, PolymerElementDeclaration} from "../../polymer/Annotations";
import {ViewMetadata} from "../../client/router/View";
import {Constructor} from "../../utils/DI";
import {Router} from "../../client/router/Router";
import {Loader} from "../../client/loader/Loader";

// Dummy factory div to construct views instances
const factory = document.createElement("div");

@Element({
	selector: "gt-view",
	template: "/assets/imports/app.html"
})
export class GtView extends PolymerElement {
	@Inject
	private loader: Loader;

	@Inject
	@On({"route-updated": "update"})
	private router: Router;
	private last_view: Constructor<PolymerElement> = null;

	private layer: number = 0;
	private current: any = null;

	private async update() {
		let new_view = this.router.activeView;
		let args = this.router.activeArguments;

		if (new_view != this.last_view) {
			document.body.classList.add("app-loader");
		}

		let meta = new_view ? Reflect.getMetadata<ViewMetadata>("view:meta", new_view) : null;

		// If the view is the same and is sticky, do not remove the
		// current element but update attributes values
		if (new_view == this.last_view && meta && meta.sticky) {
			for (let key in args) {
				this.current.setAttribute(key, args[key] != void 0 ? args[key] : null);
			}
			return;
		}

		this.current = null;

		// Remove every current children
		let views: Element[] = this.node.children;
		let tasks: Promise<any>[] = views.map(e => {
			return new Promise((res) => {
				e.classList.remove("active");
				e.addEventListener("transitionend", res);
			}).then(() => this.node.removeChild(e));
		});

		this.last_view = new_view;
		if (!new_view) return;

		// Prevent navigation during the transition
		this.router.lock(true);

		// Wait until everything is ready to create the view element
		tasks.push(this.loader.loadElement(new_view));
		await Promise.all(tasks);

		// Hide loader
		document.body.classList.remove("app-loader");

		// Construct the argument list
		let arg_string: string[] = [];
		for (let key in args) {
			if (args[key] != void 0) {
				let arg_value = args[key].replace(/./g, (s) => `&#${s.charCodeAt(0)};`);
				arg_string.push(` ${key}='${arg_value}'`);
			}
		}

		// Use a crazy HTML generation system to create the element since we need to have
		// router-provided attributes defined before the createdCallback() method is called
		let decl = Reflect.getMetadata<PolymerElementDeclaration>("polymer:declaration", new_view);
		if (!decl) return;
		factory.innerHTML = `<${decl.selector}${arg_string.join()}></${decl.selector}>`;

		// Element has been constructed by the HTML parser
		let element = <any> factory.firstElementChild;

		// Ensure the new element is over older ones no matter what
		element.style.zIndex = ++this.layer;

		// Insert the element in the DOM
		this.current = element;
		this.node.appendChild(element);

		// Add active class two frames from now
		requestAnimationFrame(() => requestAnimationFrame(() => element.classList.add("active")));

		// Unlock router and allow navigation again
		this.router.lock(false);
	}
}
