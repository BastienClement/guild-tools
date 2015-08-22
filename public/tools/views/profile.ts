import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind, PolymerElement } from "elements/polymer";
import { Router, View, Arg } from "client/router";

Router.declareTabs("profile", [
	{ title: "Profile", link: "/profile" }
]);

@View("profile", "gt-profile", "/profile")
export class Profile extends PolymerElement {
	init() {
		console.log("profile init");
	}

	detached() {
		console.log("profile detached");
	}
}
