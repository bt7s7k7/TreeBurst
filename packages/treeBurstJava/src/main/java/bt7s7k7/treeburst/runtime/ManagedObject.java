package bt7s7k7.treeburst.runtime;

import bt7s7k7.treeburst.support.ManagedValue;

public abstract class ManagedObject extends ManagedValue {
	public String name = null;

	public boolean hasGetters() {
		return false;
	}

	public boolean hasSetters() {
		return false;
	}

	public final ManagedObject prototype;

	public ManagedValue getOwnProperty(String name) {
		return null;
	}

	@Override
	public String toString() {
		return this.name != null ? "[" + this.name + "]" : "[object " + (this.prototype == null ? "null" : (this.prototype.name == null ? "<anon>" : this.prototype.name)) + "]";
	}

	public ManagedObject(ManagedObject prototype) {
		this.prototype = prototype;
	}
}
