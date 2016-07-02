import moment from "moment";
import {User} from "../../services/roster/RosterService";
import {View} from "../../client/router/View";
import {Element, Inject, Property} from "../../polymer/Annotations";
import {GtDialog} from "../widgets/GtDialog";
import {GtButton} from "../widgets/GtButton";
import {GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {Server} from "../../client/server/Server";

interface ServerInfos {
	name: string;
	version: string;
	start: number;
	uptime: number;
}

interface RuntimeInfos {
	cores: number;
	memory_used: number;
	memory_free: number;
	memory_total: number;
	memory_max: number;
}

interface SocketInfos {
	id: string;
	name: string;
	user?: User;
	state: string;
	uptime: number;
	opener: { ip: string, ua: string };
	channels: [number, string][];
}

@View("server-status", () => [{title: "Server status", link: "/server-status", active: true}])
@Element({
	selector: "gt-server-status",
	template: "/assets/views/server-status.html",
	dependencies: [GtBox, GtButton, GtDialog]
})
export class GtServerStatus extends PolymerElement {
	@Inject
	private server: Server;

	// The server-status channel
	private channel = this.server.openServiceChannel("server-status");
	private channel_ready = false;

	// Update ticker
	private ticker: any;
	private tickerSocket: any;

	// Infos objects
	private serverInfos: ServerInfos;
	private hostInfos: ServerInfos;
	private runtimeInfos: RuntimeInfos;
	private socketsInfos: SocketInfos[];

	@Property({computed: "serverInfos.start"})
	private get startTime(): string {
		return moment(this.serverInfos.start).format("DD/MM/YYYY HH:mm:ss");
	}

	private async attached() {
		try {
			await this.channel.open();
			this.channel_ready = true;
			this.refreshAll();
			this.fetchSocketsInfos();
			this.ticker = setInterval(() => this.refreshAll(), 1000);
			this.tickerSocket = setInterval(() => this.fetchSocketsInfos(), 2000);
		} catch (e) {
			console.error(e);
			this.$.fail.show();
			// Failure
		}
	}

	private async refreshAll() {
		this.fetchServerInfos();
		this.fetchRuntimeInfos();
		this.fetchHostInfos();
	}

	private async fetchServerInfos() {
		this.serverInfos = await this.channel.request<ServerInfos>("server-infos", void 0, void 0, true);
		this.set("serverInfos.version", this.serverInfos.version.slice(0, 15));
	}

	private async fetchHostInfos() {
		this.hostInfos = await this.channel.request<ServerInfos>("host-infos", void 0, void 0, true);
	}

	private async fetchRuntimeInfos() {
		this.runtimeInfos = await this.channel.request<RuntimeInfos>("runtime-infos", void 0, void 0, true);
	}

	private async fetchSocketsInfos() {
		let sockets = await this.channel.request<SocketInfos[]>("sockets-infos", void 0, void 0, true);
		sockets.forEach(infos => {
			infos.channels.sort(([a], [b]) => {
				return a - b;
			});
		});
		sockets.sort((a, b) => {
			return b.uptime - a.uptime;
		});
		this.socketsInfos = sockets;
	}

	private username(user: User) {
		return user ? user.name : "--";
	}

	private duration(dt: number) {
		let time = moment(dt).utc().format("DDD:HH:mm:ss").split(":");
		time[0] = String(Number(time[0]) - 1);
		if (time[0] == "0") time.shift();
		return time.join(":");
	}

	private formatMemory(memory: number) {
		return Math.round(memory / 1024 / 1024) + " MB";
	}

	private async runGC() {
		await this.channel.request("run-gc");
		this.fetchRuntimeInfos();
	}

	private killSocket(ev: PolymerModelEvent<SocketInfos>) {
		this.channel.send("kill-socket", ev.model.item.id);
	}

	private detached() {
		clearInterval(this.ticker);
		clearInterval(this.tickerSocket);
		this.channel.close();
	}
}
