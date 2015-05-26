export function dialog(title: string, message: string, infos: string = "", actions?: { [labl: string]: Function }) {
	const frame = <HTMLDivElement> document.querySelector("#error");
	
	frame.querySelector(".title").textContent = title;
	frame.querySelector(".text").textContent = message;
	frame.querySelector(".infos").textContent = infos;
	
	const actions_container = <HTMLDivElement> frame.querySelector(".actions");
	actions_container.innerHTML = "";
	
	function action_handler(label: string) {
		return () => actions[label]();
	}
	
	for (let label in actions) {
		const link = document.createElement("a");
		link.innerText = label;
		link.onclick = action_handler(label);
		actions_container.appendChild(link);
	}
	
	frame.style.display = "block";
}

let status_timeout: number = null;
export function status(message?: string, sticky: boolean = false) {
	const frame = <HTMLDivElement> document.querySelector("#status");
	if (message) {
		clearTimeout(status_timeout);
		frame.innerText = message;
		frame.classList.add("visible");
		if (!sticky) {
			status_timeout = setTimeout(() => status(), 2000);
		}
	} else {
		clearTimeout(status_timeout);
		status_timeout = null;
		frame.classList.remove("visible");
	}
}

export function server_updated() {
	const frame = <HTMLDivElement> document.querySelector("#error");
	frame.querySelector(".title").textContent = "GuildTools server updated";
	frame.querySelector(".text").textContent = "message";
	frame.querySelector(".infos").textContent = "infos";
	frame.style.display = "block";
}
