///<reference path="encoding.d.ts" />
///<reference path="es6.d.ts" />
///<reference path="jquery.d.ts" />
///<reference path="less.d.ts" />
///<reference path="pako.d.ts" />
///<reference path="polymer.d.ts" />

declare var PolymerLoader: {
	register: (element: string, bundle: string, prototype: Function) => any;
	start: () => Promise<void>;
};

declare var require: Function;
