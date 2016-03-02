import {TabsGenerator, View} from "../../client/router/View";
import {Element, Property} from "../../polymer/Annotations";
import {ProfileUser} from "ProfileUser";
import {ProfileInfo} from "./ProfileInfo";
import {ProfileChars} from "./ProfileChars";
import {PolymerElement} from "../../polymer/PolymerElement";

const ProfileTabs: TabsGenerator = (view, path, user) => [
	{ title: "Profile", link: "/profile", active: view == GtProfile }
];

@View("profile", ProfileTabs)
@Element({
	selector: "gt-profile",
	template: "/assets/views/profile.html",
	dependencies: [ProfileUser, ProfileInfo, ProfileChars]
})
export class GtProfile extends PolymerElement {
	@Property
	public user: number = this.app.user.id;
}
