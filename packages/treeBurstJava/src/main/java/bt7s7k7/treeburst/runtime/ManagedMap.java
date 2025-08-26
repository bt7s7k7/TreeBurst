package bt7s7k7.treeburst.runtime;

import java.util.HashMap;
import java.util.Map;

import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Primitive;

public class ManagedMap extends ManagedObject {
	public final HashMap<ManagedValue, ManagedValue> entries;

	public ManagedMap(ManagedObject prototype) {
		this(prototype, new HashMap<>());
	}

	public ManagedMap(ManagedObject prototype, Map<ManagedValue, ManagedValue> entries) {
		super(prototype);
		this.entries = new HashMap<>(entries);
	}

	public ManagedMap(ManagedObject prototype, HashMap<ManagedValue, ManagedValue> entries) {
		super(prototype);
		this.entries = entries;
	}

	@Override
	public boolean getProperty(String name, ExpressionResult result) {
		if (name.equals("length")) {
			result.value = Primitive.from(this.entries.size());
			return true;
		}

		return super.getProperty(name, result);
	}
}
