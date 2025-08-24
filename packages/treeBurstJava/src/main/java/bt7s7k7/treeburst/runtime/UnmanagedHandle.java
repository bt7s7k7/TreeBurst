package bt7s7k7.treeburst.runtime;

public class UnmanagedHandle extends ManagedObject {

	public final Object value;

	public UnmanagedHandle(ManagedObject prototype, Object value) {
		super(prototype);
		this.value = value;
	}

	@Override
	public String toString() {
		return this.name == null ? "<unmanaged>" : String.format("<unmanaged %s>", this.name);
	}

	public Object getValue() {
		return value;
	}
}
