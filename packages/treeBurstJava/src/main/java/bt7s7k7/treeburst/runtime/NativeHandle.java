package bt7s7k7.treeburst.runtime;

public class NativeHandle extends ManagedObject {

	public final Object value;

	public NativeHandle(ManagedObject prototype, Object value) {
		super(prototype);
		this.value = value;
	}

	@Override
	public String toString() {
		return this.name == null ? "<native>" : String.format("<native %s>", this.name);
	}

	public Object getValue() {
		return value;
	}
}
