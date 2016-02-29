
/**
 * The user information object from the server
 */
export type User = {
	id: number;
	name: string;
	group: number;
	color: string;
	officer: boolean;
	developer: boolean;
	promoted: boolean;
	member: boolean;
	roster: boolean;
	fs: boolean;
}
