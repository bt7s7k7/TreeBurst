package bt7s7k7.treeburst.runtime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.stream.Stream;

import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Primitive;

public abstract class ManagedMap extends ManagedObject implements Iterable<Map.Entry<ManagedValue, ManagedValue>> {
	public abstract int getLength();

	public abstract void clear();

	public abstract ManagedValue get(ManagedValue key);

	public abstract void set(ManagedValue key, ManagedValue value);

	public abstract Map<ManagedValue, ManagedValue> getElementsReadOnly();

	public abstract Map<ManagedValue, ManagedValue> getElementsMutable();

	public abstract Stream<ManagedValue> getKeys();

	public abstract Stream<ManagedValue> getValues();

	public abstract ManagedMap makeCopy();

	@Override
	public abstract Iterator<Entry<ManagedValue, ManagedValue>> iterator();

	@Override
	public abstract Spliterator<Entry<ManagedValue, ManagedValue>> spliterator();

	public ManagedMap(ManagedObject prototype) {
		super(prototype);
	}

	@Override
	public String getNameOrInheritedName() {
		var result = super.getNameOrInheritedName();
		if ("Map".equals(result)) return null;
		return result;
	}

	@Override
	public String kind() {
		return "map(" + this.getLength() + ")";
	}

	@Override
	public ManagedValue getOwnProperty(String name) {
		if (name.equals("length")) {
			return Primitive.from(this.getLength());
		}

		return super.getOwnProperty(name);
	}

	public static class HashMapBackedMap extends ManagedMap {
		public final Map<ManagedValue, ManagedValue> entries;

		public HashMapBackedMap(ManagedObject prototype, Map<ManagedValue, ManagedValue> entries) {
			super(prototype);
			this.entries = new HashMap<>(entries);
		}

		@Override
		public int getLength() {
			return this.entries.size();
		}

		@Override
		public void clear() {
			this.entries.clear();
		}

		@Override
		public ManagedValue get(ManagedValue key) {
			var value = this.entries.get(key);
			if (value == null) return Primitive.VOID;
			return value;
		}

		@Override
		public void set(ManagedValue key, ManagedValue value) {
			if (value == Primitive.VOID) {
				this.entries.remove(key);
				return;
			}

			this.entries.put(key, value);
		}

		@Override
		public Map<ManagedValue, ManagedValue> getElementsReadOnly() {
			return this.entries;
		}

		@Override
		public Map<ManagedValue, ManagedValue> getElementsMutable() {
			return this.entries;
		}

		@Override
		public Iterator<Entry<ManagedValue, ManagedValue>> iterator() {
			return this.entries.entrySet().iterator();
		}

		@Override
		public Spliterator<Entry<ManagedValue, ManagedValue>> spliterator() {
			return this.entries.entrySet().spliterator();
		}

		@Override
		public Stream<ManagedValue> getKeys() {
			return this.entries.keySet().stream();
		}

		@Override
		public Stream<ManagedValue> getValues() {
			return this.entries.values().stream();
		}

		@Override
		public ManagedMap makeCopy() {
			return new HashMapBackedMap(this.prototype, new HashMap<>(this.entries));
		}
	}

	public static ManagedMap empty(ManagedObject prototype) {
		return new HashMapBackedMap(prototype, new HashMap<>());
	}

	public static ManagedMap withElements(ManagedObject prototype, Map<ManagedValue, ManagedValue> entries) {
		return new HashMapBackedMap(prototype, new HashMap<>(entries));
	}

	public static ManagedMap fromMutableMap(ManagedObject prototype, Map<ManagedValue, ManagedValue> entries) {
		return new HashMapBackedMap(prototype, entries);
	}
}
