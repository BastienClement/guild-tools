import {Element, Inject, Property, Listener} from "../../polymer/Annotations";
import {GtTimeago} from "../misc/GtTimeago";
import {BnetThumb} from "../misc/BnetThumb";
import {GtButton} from "../widgets/GtButton";
import {GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {RosterService, Char} from "../../services/roster/RosterService";

///////////////////////////////////////////////////////////////////////////////
// <profile-chars-card>

@Element({
	selector: "profile-chars-card",
	template: "/assets/views/profile.html",
	dependencies: [GtBox, GtButton, BnetThumb, GtTimeago]
})
export class ProfileCharsCard extends PolymerElement {
	@Inject
	private roster: RosterService;

	@Property
	public id: number;
	@Property
	public editable: boolean
	@Property
	public char: Char;

	private update_pending = false;

	@Property({computed: "char.last_update update_pending"})
	public get updatable(): boolean {
		if (this.update_pending) return false;
		let dt = Date.now() - this.char.last_update;
		return dt > 1000 * 60 * 15;
	}

	// Role
	private SetRole(role: string) {
		if (this.char.role == role) return;
		this.roster.changeRole(this.id, role);
	}

	@Listener("role-tank.click")
	public SetRoleTank() {
		this.SetRole("TANK");
	}

	@Listener("role-healing.click")
	public SetRoleHealing() {
		this.SetRole("HEALING");
	}

	@Listener("role-dps.click")
	public SetRoleDPS() {
		this.SetRole("DPS");
	}

	// Control buttons
	@Listener("btn-disable.click")
	public Disable() {
		this.roster.disableChar(this.id);
	}

	@Listener("btn-enable.click")
	public Enable() {
		this.roster.enableChar(this.id);
	}

	@Listener("btn-main.click")
	public Promote() {
		this.roster.promoteChar(this.id);
	}

	@Listener("btn-remove.click")
	public Remove() {
		this.roster.removeChar(this.id);
	}

	@Listener("btn-update.click")
	public async Update() {
		this.update_pending = true;
		try {
			await this.roster.updateChar(this.id);
		} finally {
			this.update_pending = false;
		}
	}
}
