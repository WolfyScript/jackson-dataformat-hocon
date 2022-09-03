package com.wolfyscript.jackson.dataformat.hocon.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Configuration that = (Configuration) o;
		return Float.compare(that.value, value) == 0 && Objects.equals(something, that.something) && Objects.equals(context, that.context);
	}

	@Override
	public int hashCode() {
		return Objects.hash(something, context, value);
	}

	public static class Context {
			public Lib lib;

			public Context() { }

			public Context(Lib lib) {
				this.lib = lib;
			}

			@Override
			public boolean equals(Object o) {
				if (this == o) return true;
				if (o == null || getClass() != o.getClass()) return false;
				Context context = (Context) o;
				return Objects.equals(lib, context.lib);
			}

			@Override
			public int hashCode() {
				return Objects.hash(lib);
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

			@Override
			public boolean equals(Object o) {
				if (this == o) return true;
				if (o == null || getClass() != o.getClass()) return false;
				Lib lib = (Lib) o;
				return Objects.equals(foo, lib.foo) && Objects.equals(whatever, lib.whatever);
			}

			@Override
			public int hashCode() {
				return Objects.hash(foo, whatever);
			}
		}
}
