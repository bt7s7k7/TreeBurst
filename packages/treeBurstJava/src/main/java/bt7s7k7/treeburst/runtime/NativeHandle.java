package bt7s7k7.treeburst.runtime;

import java.util.Objects;

public class NativeHandle extends ManagedObject {

	public final Object value;

	public NativeHandle(ManagedObject prototype, Object value) {
		super(prototype);
		this.value = value;
	}

	@Override
	public String toString() {
		return this.name == null ? "<native " + this.value.getClass().getSimpleName() + ">" : "<native " + this.name + ">";
	}

	public Object getValue() {
		return this.value;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.value);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj instanceof NativeHandle other) return Objects.equals(this.value, other.value);
		return false;
	}
}
