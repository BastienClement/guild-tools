interface TextEncoder {
	encoding: string;
	encode(buffer: string, options?: { stream: boolean }): Uint8Array;
}

interface TextDecoder {
	encoding: string;
	decode(buffer?: ArrayBufferView, options?: { stream: boolean }): string;
}

var TextEncoder: {
	new(encoding: string): TextEncoder;
};

var TextDecoder: {
	new(encoding: string): TextDecoder;
};
