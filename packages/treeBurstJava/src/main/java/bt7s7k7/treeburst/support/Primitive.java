package bt7s7k7.treeburst.support;

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

		public Number(double value) {
			this.value = value;
		}
	}

	public static class String extends ManagedValue {
		public final java.lang.String value;

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
}
