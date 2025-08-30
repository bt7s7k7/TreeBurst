package bt7s7k7.treeburst.runtime;

import bt7s7k7.treeburst.support.ManagedValue;

public abstract class ManagedObject extends ManagedValue {
	public boolean hasGetters;
	public boolean hasSetters;

	public String name = null;

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
		this.hasSetters = prototype != null && prototype instanceof ManagedTable parentTable ? parentTable.hasSetters : false;
	}
}
