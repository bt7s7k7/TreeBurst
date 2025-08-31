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

	public String kind() {
		return "object";
	}

	public String getNameOrInheritedName() {
		String name = null;

		if (this.name != null) {
			name = "::" + this.name;
		} else {
			if (this.prototype == null) {
				name = "null";
			} else if (this.prototype.name != null) {
				name = this.prototype.name;
				if (name.endsWith(".prototype")) {
					name = name.substring(0, name.length() - 10);
				}
			}
		}

		return name;
	}

	@Override
	public String toString() {
		var name = this.getNameOrInheritedName();
		if (name == null) {
			return this.kind();
		} else {
			return "::" + this.kind() + (name == null ? "" : " " + name);
		}
	}

	public ManagedObject(ManagedObject prototype) {
		this.prototype = prototype;
		this.hasSetters = prototype != null && prototype instanceof ManagedTable parentTable ? parentTable.hasSetters : false;
	}
}
