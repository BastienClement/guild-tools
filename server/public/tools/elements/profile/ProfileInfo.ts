import {Element, Inject, Property} from "../../polymer/Annotations";
import {GtButton} from "../widgets/GtButton";
import {GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {ProfileService, ProfileData} from "../../services/profile/ProfileService";
import moment from "moment";

///////////////////////////////////////////////////////////////////////////////
// <profile-infos>

@Element({
	selector: "profile-info",
	template: "/assets/views/profile.html",
	dependencies: [GtBox, GtButton]
})
export class ProfileInfo extends PolymerElement {
	/**
	 * ProfileService instance
	 */
	@Inject
	private service: ProfileService;

	/**
	 * The user id
	 */
	@Property({observer: "FetchProfile"})
	public user: number;

	/**
	 * User's profile data
	 */
	private data: ProfileData;

	/**
	 * Return the user's realname
	 */
	@Property({computed: "data"})
	public get realname() {
		return this.data.realname || "—";
	}

	/**
	 * Return the user's BattleTag
	 */
	@Property({computed: "data"})
	public get btag() {
		return this.data.btag || "—";
	}

	/**
	 * Return the user's phone number
	 * @returns {string}
	 */
	@Property({computed: "data"})
	public get phone() {
		if (!this.data.phone) return "—";
		let phone = this.data.phone;

		// Attempt to format the phone number
		[
			"+33 x xx xx xx xx",
			"+41 xx xxx xx xx",
			"+32 xxx xx xx xx",
			"+222 xxxx xxxx"
		].forEach(format => {
			let prefix_len = format.indexOf(" ");
			let prefix = format.slice(0, prefix_len);

			if (prefix == phone.slice(0, prefix_len)) {
				let digits = phone.slice(prefix_len).split("");
				let formatted = prefix;

				for (let i = prefix_len; i < format.length; i++) {
					let char = format[i];
					if (char == "x" && digits.length != 0) {
						char = digits.shift();
					}
					formatted += char;
				}

				if (digits.length != 0) {
					formatted += " " + digits.join("")
				}

				phone = formatted;
			}
		});

		return phone;
	}

	/**
	 * Return the user's age
	 */
	@Property({computed: "data"})
	public get age() {
		if (!this.data.birthday) return "—";
		let now = moment();
		let birth = moment(this.data.birthday);
		return now.diff(birth, "years").toString();
	}

	/**
	 * Return the user's mail
	 */
	@Property({computed: "data"})
	public get mail() {
		return this.data.mail || "—";
	}

	/**
	 * Return the user's real-life location
	 * @returns {string}
	 */
	@Property({computed: "data"})
	public get location() {
		return this.data.location || "—";
	}

	/**
	 * Fetch the user's profile.
	 * Called automatically when the user is is available.
	 */
	private async FetchProfile() {
		if (this.fetching) return;
		this.fetching = true;
		this.data = await this.service.userProfile(this.user);
		this.fetching = false;
	}

	private fetching: boolean = false;

	/**
	 * Check if the user profile is editable by the current user
	 */
	@Property({computed: "user"})
	private get editable(): boolean {
		return this.app.user.id == this.user || this.app.user.promoted;
	}

	/**
	 * Click handler for the Edit button
	 */
	private EditProfile() {
		let base = this.app.dev ? "/auth" : "//auth.fromscratch.gg";
		document.location.href = base + "/user/" + this.user;
	}
}
