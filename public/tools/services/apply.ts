import { Component } from "utils/di";
import { Service } from "utils/service";
import { join, synchronized } from "utils/async";
import { Server, ServiceChannel } from "client/server";
import { PolymerElement, Provider, Inject, Property } from "elements/polymer";

/**
 * An application meta-data
 */
export interface Apply {
	id: number;
	user: number;
	date: string;
	stage: number;
	updated: string;
}

/**
 * Apply service
 */
@Component
export class ApplyService extends Service {
	constructor(private server: Server) {
		super();
	}
	
	// Profile channel
	private channel = this.server.openServiceChannel("apply");
	
	// Data cache
	private applys = new Map<number, Apply>();
	private unread = new Map<number, boolean>();
	
	// Load open applys data
	@join public async openApplysList() {
		let data = await this.channel.request<[Apply, boolean][]>("open-list");
		let list: number[] = [];
		for (let [apply, unread] of data) {
			let id = apply.id;
			this.applys.set(id, apply);
			this.unread.set(id, unread);
			list.push(id);
		}
		return list;
	}
	
	// Check the unread state for an apply
	public unreadState(apply: number): boolean {
		return this.unread.get(apply);
	}
	
	// Return apply data
	public async applyData(id: number) {
		if (!this.applys.has(id)) {
			let data = await this.channel.request<Apply>("apply-data", id);
			this.applys.set(id, data);
		}
		
		return this.applys.get(id);
	}
	
	// Close the channel when the apply service is paused
	// and clear local caches
	private pause() {
		this.channel.close();
		this.applys.clear();
		this.unread.clear();
	}
}

/**
 * Translate numeric apply stages to textual names
 */
@Provider("apply-stage-name")
class StageNameProvider extends PolymerElement {
	@Property({ observer: "update" })
	public stage: number;
	
	@Property({ notify: true })
	public name: string;

	public update() {
		this.name = this.map(this.stage);
	}
	
	private map(stage: number) {
		switch (stage) {
			case 0: return "Pending";
			case 1: return "Review";
			case 2: return "Trial";
			case 3: return "Accepted";
			case 4: return "Refused";
			case 5: return "Archived";
			default: return "Unknown";    
		}
	}
}

/**
 * Application data fetcher
 */
@Provider("apply-data")
class DataProvider extends PolymerElement {
	@Inject
	private service: ApplyService;
	
	@Property({ observer: "update" })
	public apply: number;
	
	@Property({ notify: true })
	public data: Apply;

	@join public async update() {
		this.data = await this.service.applyData(this.apply);
	}
}
