package com.jasonclawson.jackson.dataformat.hocon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class Configuration {
	
		public String something;
		public Context context;
		public float value;

		public Configuration() { }

		public Configuration(String something, Context context, float value) {
			this.something = something;
			this.context = context;
			this.value = value;
		}
		
		public static class Context {
			public Lib lib;

			public Context() { }

			public Context(Lib lib) {
				this.lib = lib;
			}
		}
		
		public static class Lib {
			public String foo;
			public String whatever;

			public Lib() { }

			public Lib(String foo, String whatever) {
				this.foo = foo;
				this.whatever = whatever;
			}

		}
}
