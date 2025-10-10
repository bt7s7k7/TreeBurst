package bt7s7k7.treeburst.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Primitive;

public class ManagedMap extends ManagedObject {
	public final LinkedHashMap<ManagedValue, ManagedValue> entries;

	protected ManagedMap(ManagedObject prototype, LinkedHashMap<ManagedValue, ManagedValue> entries) {
		super(prototype);
		this.entries = entries;
	}

	public static ManagedMap empty(ManagedObject prototype) {
		return new ManagedMap(prototype, new LinkedHashMap<>());
	}

	public static ManagedMap withEntries(ManagedObject prototype, Map<ManagedValue, ManagedValue> entries) {
		return new ManagedMap(prototype, new LinkedHashMap<>(entries));
	}

	public static ManagedMap fromMutableEntries(ManagedObject prototype, LinkedHashMap<ManagedValue, ManagedValue> entries) {
		return new ManagedMap(prototype, entries);
	}

	@Override
	public String getNameOrInheritedName() {
		var result = super.getNameOrInheritedName();
		if ("Map".equals(result)) return null;
		return result;
	}

	@Override
	public String kind() {
		return "map(" + this.entries.size() + ")";
	}

	@Override
	public ManagedValue getOwnProperty(String name) {
		if (name.equals("length")) {
			return Primitive.from(this.entries.size());
		}

		return super.getOwnProperty(name);
	}
}
