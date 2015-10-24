
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

interface SystemInterface {
	import<T>(...modules: any[]): Promise<T>;
}

declare const System: SystemInterface;
declare const APP: boolean;

interface StyleFixInterface {
	link(e: HTMLLinkElement): void;
	styleElement(e: HTMLStyleElement): void;
	styleAttribute(e: HTMLElement): void;
	fix(css: string, raw?: boolean, element?: HTMLElement): string;
}

declare const StyleFix: StyleFixInterface;

interface TraceurModule{
	Compiler: TraceurCompilerStatic;
}

interface TraceurCompiler {
	compile(source: string, filename: string): string;
	sourceMapCache_: string;
}

interface TraceurCompilerStatic {
	new (options?: any): TraceurCompiler;
}

declare const traceur: TraceurModule;

interface PromiseResolver<T> {
	resolve(value?: T | PromiseLike<T>): void;
	reject(reason: any): void;
	promise: Promise<T>;
}

interface PromiseConstructor {
	defer<T>(): PromiseResolver<T>;
	onload<T extends { onload: Function; onerror?: Function }>(node: T): Promise<T>;
}

interface Promise<T> {
	finally(finalized: () => void): Promise<T>;
}