package bt7s7k7.treeburst.runtime;

import java.util.HashMap;
import java.util.Map;

import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Primitive;

public class ManagedMap extends ManagedObject {
	public final Map<ManagedValue, ManagedValue> entries = new HashMap<>();

	public ManagedMap(ManagedObject prototype) {
		super(prototype);
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
