import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind, PolymerElement } from "elements/polymer";
import { Router, View, Arg } from "client/router";
import { Server } from "client/server";
import { Roster, User, Char } from "services/roster";
import { GtBox, GtButton, BnetThumb, DataMain, DataUser, DataClass, DataRace, DataRank } from "elements/defs";

Router.declareTabs("profile", [
	{ title: "Profile", link: "/profile" }
]);

@Element("profile-user", "/assets/views/profile.html")
@Dependencies(GtBox, BnetThumb, DataMain, DataUser, DataClass, DataRace, DataRank)    
export class ProfileUser extends PolymerElement {
	@Property public user: number;
}

@Element("profile-infos", "/assets/views/profile.html")
@Dependencies(GtBox, GtButton, DataUser, DataRank)    
export class ProfileInfos extends PolymerElement {
	@Property public user: number;
	
	@Property({ computed: "user" })
	private get editable(): boolean {
		return this.app.user.id == this.user;
	}
}

@View("profile", "gt-profile", "/profile(/:id)?")
@Dependencies(ProfileUser, ProfileInfos)
export class Profile extends PolymerElement {
	@Arg("id")
	public user: number = this.app.user.id;
}
