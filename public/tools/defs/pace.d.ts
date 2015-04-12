interface Pace {
	once: (event: string, cb: Function) => void;
	on: (event: string, cb: Function) => void;
	running: boolean;
	restart: () => void;
}

declare var Pace: Pace;
declare var _pace_gt_get: () => number;
declare var _pace_bn_get: () => number;
