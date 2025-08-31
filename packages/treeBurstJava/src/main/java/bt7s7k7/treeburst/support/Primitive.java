package bt7s7k7.treeburst.support;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Primitive extends ManagedValue {
	public final static ManagedValue VOID = new Primitive() {
		@Override
		public java.lang.String toString() {
			return "void";
		}
	};

	public final static ManagedValue NULL = new Primitive() {
		@Override
		public java.lang.String toString() {
			return "null";
		}
	};

	public static class Number extends Primitive {
		public final double value;

		@Override
		public java.lang.String toString() {
			return "[number " + this.value + "]";
		}

		@Override
		public int hashCode() {
			return Double.hashCode(this.value);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Number other) return this.value == other.value;
			return false;
		}

		public Number(double value) {
			this.value = value;
		}
	}

	public static class String extends Primitive {
		public final java.lang.String value;

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof String other) return Objects.equals(this.value, other.value);
			return false;
		}

		@Override
		public int hashCode() {
			return this.value.hashCode();
		}

		private static final Pattern STRING_ESCAPE_CHARACTERS = Pattern.compile("[\\\\\\t" + Pattern.quote("\b") + "\\n\\r\\f\"]");

		public static java.lang.String escapeString(java.lang.String input) {
			return STRING_ESCAPE_CHARACTERS.matcher(input).replaceAll(match -> {
				var c = match.group().charAt(0);
				switch (c) {
					case '\t':
						return Matcher.quoteReplacement("\\t");
					case '\b':
						return Matcher.quoteReplacement("\\b");
					case '\r':
						return Matcher.quoteReplacement("\\r");
					case '\n':
						return Matcher.quoteReplacement("\\n");
					case '\f':
						return Matcher.quoteReplacement("\\f");
					case '"':
						return Matcher.quoteReplacement("\\\"");
					case '\\':
						return Matcher.quoteReplacement("\\\\");
					default:
						throw new IllegalArgumentException("Unexpected argument in from string escape function: " + (int) c);
				}
			});
		}

		@Override
		public java.lang.String toString() {
			return "[string \"" + escapeString(this.value) + "\"]";
		}

		public String(java.lang.String value) {
			this.value = value;
		}
	}

	public static class Boolean extends Primitive {
		public final boolean value;

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Boolean other) return this.value == other.value;
			return false;
		}

		@Override
		public int hashCode() {
			return java.lang.Boolean.hashCode(this.value);
		}

		@Override
		public java.lang.String toString() {
			return "[boolean " + this.value + "]";
		}

		public Boolean(boolean value) {
			this.value = value;
		}
	}

	public static Number from(double value) {
		return new Number(value);
	}

	public static String from(java.lang.String value) {
		return new String(value);
	}

	public static Boolean from(boolean value) {
		return new Boolean(value);
	}

	public static final Primitive.Boolean FALSE = Primitive.from(false);
	public static final Primitive.Boolean TRUE = Primitive.from(true);
	public static final Primitive.Number ZERO = Primitive.from(0);
	public static final Primitive.String EMPTY_STRING = Primitive.from("");
}
