package bt7s7k7.treeburst.runtime;

import java.util.HashMap;
import java.util.Map;

import bt7s7k7.treeburst.support.ManagedValue;

public class ManagedTable extends ManagedObject {
	public ManagedTable(ManagedObject prototype) {
		super(prototype);
	}

	protected final Map<String, ManagedValue> properties = new HashMap<>();

	@Override
	public boolean getProperty(String name, ExpressionResult result) {
		if (properties.containsKey(name)) {
			result.value = properties.get(name);
			return true;
		}
		return super.getProperty(name, result);
	}

	public boolean declareProperty(String name, ManagedValue value) {
		if (properties.containsKey(name)) {
			return false;
		}
		properties.put(name, value);

		if (this.name != null && value instanceof ManagedObject managedObject && managedObject.name == null) {
			managedObject.name = this.name + "." + name;
		}

		return true;
	}

	public boolean setProperty(String name, ManagedValue value) {
		if (!properties.containsKey(name)) {
			return false;
		}
		properties.put(name, value);
		return true;
	}
}
