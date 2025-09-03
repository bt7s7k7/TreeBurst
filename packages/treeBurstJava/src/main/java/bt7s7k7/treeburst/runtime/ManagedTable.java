package bt7s7k7.treeburst.runtime;

import java.util.HashMap;
import java.util.Map;

import bt7s7k7.treeburst.support.ManagedValue;

public class ManagedTable extends ManagedObject {
	public ManagedTable(ManagedObject prototype) {
		this(prototype, new HashMap<>());
	}

	public ManagedTable(ManagedObject prototype, Map<String, ManagedValue> properties) {
		super(prototype);
		this.properties = properties;
	}

	public final Map<String, ManagedValue> properties;

	@Override
	public ManagedValue getOwnProperty(String name) {
		var value = this.properties.get(name);
		if (value != null) return value;
		return super.getOwnProperty(name);
	}

	public boolean declareProperty(String name, ManagedValue value) {
		if (this.properties.containsKey(name)) {
			return false;
		}
		this.properties.put(name, value);

		if (this.name != null && value instanceof ManagedObject managedObject && managedObject.name == null) {
			managedObject.name = this.name + "." + name;
		}

		if (name.startsWith("set_")) {
			this.hasSetters = true;
		} else if (name.startsWith("get_")) {
			this.hasGetters = true;
		}

		return true;
	}

	public boolean setOwnProperty(String name, ManagedValue value) {
		if (!this.properties.containsKey(name)) {
			return false;
		}

		this.properties.put(name, value);
		return true;
	}
}
