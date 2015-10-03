import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind, PolymerElement } from "elements/polymer";
import { Router, View, Arg } from "client/router";
import { Server } from "client/server";
import { Roster, User, Char } from "services/roster";
import { GtBox, BnetThumb, DataMain, DataUser, DataClass, DataRace, DataRank } from "elements/defs";

Router.declareTabs("profile", [
	{ title: "Profile", link: "/profile" }
]);

@Element("profile-user", "/assets/views/profile.html")
@Dependencies(GtBox, BnetThumb, DataMain, DataUser, DataClass, DataRace, DataRank)    
export class ProfileUser extends PolymerElement {
	@Property(Number)
	private user: number;
}

@View("profile", "gt-profile", "/profile")
@Dependencies(ProfileUser)    
export class Profile extends PolymerElement {
	@Inject
	private server: Server;
	
	@Property(Number)
	private user = this.server.user.id;
	
	init() {
		console.log("profile init");
	}

	detached() {
		console.log("profile detached");
	}
}
