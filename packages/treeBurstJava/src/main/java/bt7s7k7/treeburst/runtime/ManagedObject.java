package bt7s7k7.treeburst.runtime;

import bt7s7k7.treeburst.support.ManagedValue;

public abstract class ManagedObject extends ManagedValue {
	public String name = null;

	public final ManagedObject prototype;

	public boolean getProperty(String name, ExpressionResult result) {
		if (this.prototype != null) {
			return this.prototype.getProperty(name, result);
		}

		return false;
	}

	@Override
	public String toString() {
		return this.name != null ? "[" + this.name + "]" : "[object " + (this.prototype == null ? "null" : (this.prototype.name == null ? "<anon>" : this.prototype.name)) + "]";
	}

	public ManagedObject(ManagedObject prototype) {
		this.prototype = prototype;
	}
}
