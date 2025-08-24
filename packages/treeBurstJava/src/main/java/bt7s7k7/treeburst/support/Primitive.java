package bt7s7k7.treeburst.support;

import java.util.Objects;

public abstract class Primitive {
	public final static ManagedValue VOID = new ManagedValue() {
		@Override
		public java.lang.String toString() {
			return "void";
		}
	};

	public final static ManagedValue NULL = new ManagedValue() {
		@Override
		public java.lang.String toString() {
			return "null";
		}
	};

	public static class Number extends ManagedValue {
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

	public static class String extends ManagedValue {
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

		@Override
		public java.lang.String toString() {
			return "[string " + this.value + "]";
		}

		public String(java.lang.String value) {
			this.value = value;
		}
	}

	public static class Boolean extends ManagedValue {
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
}
