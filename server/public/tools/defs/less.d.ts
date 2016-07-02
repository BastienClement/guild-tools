
interface LessRenderResults {
	css: string;
	imports: string[];
}

interface Less {
	render(less_code: string, options?: any): Promise<LessRenderResults>;
}

declare var less: Less;
