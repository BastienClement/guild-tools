import { Element, Property, Listener, Dependencies, PolymerElement, PolymerEvent } from "elements/polymer";

@Element("gt-title-bar", "/assets/imports/app.html")
export class GtTitleBar extends PolymerElement {
	
}

@Element("gt-sidebar", "/assets/imports/app.html")
export class GtSidebar extends PolymerElement {
	
}

@Element("gt-app", "/assets/imports/app.html")
@Dependencies(GtTitleBar, GtSidebar)	
export class GtApp extends PolymerElement {
	
}
