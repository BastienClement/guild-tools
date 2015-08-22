///<reference path="encoding.d.ts" />
///<reference path="es6.d.ts" />
///<reference path="jquery.d.ts" />
///<reference path="less.d.ts" />
///<reference path="pako.d.ts" />
///<reference path="polymer.d.ts" />
///<reference path="moment.d.ts" />

declare var require: Function;

declare module Reflect {
	function getMetadata<T>(metadataKey: string, target: any, propertyKey?: string): T;
	function getOwnMetadata<T>(metadataKey: string, target: any, propertyKey?: string): T;
	function defineMetadata(metadataKey: string, metadataValue: any, target: any, propertyKey?: string): void;
	function hasMetadata(metadataKey: string, target: any, propertyKey?: string): boolean;
	function hasOwnMetadata(metadataKey: string, target: any, propertyKey?: string): boolean;
	function deleteMetadata(metadataKey: string, target: any, propertyKey?: string): void;
	function getMetadataKeys(target: any, propertyKey?: string): string[];
	function getOwnMetadataKeys(target: any, propertyKey?: string): string[];
}
