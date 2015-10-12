module TraceurWorker {
	importScripts("/assets/javascripts/traceur.js");
	importScripts("/assets/javascripts/source-map.js");
	
	self.onmessage = function(m) {
		var index: number;
		var value: any;
		
		try {
			var compiler = new traceur.Compiler(m.data.config);
			var filename = m.data.filename;
			value = compiler.compile(m.data.source, filename);
			index = 0;
			
			var ts_map = m.data.config.tsSourceMap;
			var traceur_map = compiler.sourceMapCache_;
			
			if (ts_map && traceur_map) {
				ts_map = new sourceMap.SourceMapConsumer(ts_map);
				traceur_map = new sourceMap.SourceMapConsumer(traceur_map);
				
				var gen = new sourceMap.SourceMapGenerator({
					file: filename
				});
				
				ts_map.eachMapping(function(mapping: any) {
					traceur_map.allGeneratedPositionsFor({
						source: filename,
						line: mapping.generatedLine,
						column: mapping.generatedColumn
					}).forEach(function(m: any) {
						gen.addMapping({
							source: mapping.source,
							original: { line: mapping.originalLine, column: mapping.originalColumn },
							generated: m
						});
					});
				});
				
				value = value.replace(/(sourceMappingURL=data:application\/json;base64,)[^\n]+/, "$1" + btoa(gen.toString()));
			}
		} catch(e) {
			value = e[0];
			index = 1;
			console.error(e);
		}
		
		self.postMessage({
			rid: m.data.rid,
			index: index,
			value: value
		}, void 0);
	};
}
