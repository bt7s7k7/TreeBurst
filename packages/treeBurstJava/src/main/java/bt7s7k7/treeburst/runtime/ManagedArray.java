package bt7s7k7.treeburst.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;

import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public abstract class ManagedArray extends ManagedObject implements Iterable<ManagedValue> {

	public ManagedArray(ManagedObject prototype) {
		super(prototype);
	}

	public abstract int getLength();

	public ManagedValue get(int index) {
		return this.getElementsReadOnly().get(index);
	}

	public void set(int index, ManagedValue value) {
		this.getElementsMutable().set(index, value);
	}

	public abstract void clear();

	public abstract ManagedArray makeCopy();

	public abstract ManagedArray makeView(int from, int to);

	public abstract List<ManagedValue> getElementsReadOnly();

	public abstract List<ManagedValue> getElementsMutable();

	@Override
	public Iterator<ManagedValue> iterator() {
		return this.getElementsReadOnly().iterator();
	}

	@Override
	public Spliterator<ManagedValue> spliterator() {
		return this.getElementsReadOnly().spliterator();
	}

	public int normalizeIndex(int index, ExpressionResult result) {
		if (index < 0) {
			index = this.getLength() + index;
		}

		if (index < 0 || index >= this.getLength()) {
			result.setException(new Diagnostic("Index " + index + " out of range of array of length " + this.getLength(), Position.INTRINSIC));
			return 0;
		}

		return index;
	}

	public int normalizeLimit(int limit, ExpressionResult result) {
		if (limit < 0) {
			limit = this.getLength() + limit;
		}

		if (limit < 0 || limit > this.getLength()) {
			result.setException(new Diagnostic("Index " + limit + " out of range of array of length " + this.getLength(), Position.INTRINSIC));
			return 0;
		}

		return limit;
	}

	@Override
	public String kind() {
		return "array(" + this.getLength() + ")";
	}

	@Override
	public String getNameOrInheritedName() {
		var result = super.getNameOrInheritedName();
		if ("Array".equals(result)) return null;
		return result;
	}

	@Override
	public ManagedValue getOwnProperty(String name) {
		if ("length".equals(name)) {
			return Primitive.from(this.getLength());
		}

		return super.getOwnProperty(name);
	}

	public static class ListBackedArray extends ManagedArray {
		public boolean immutable;
		public List<ManagedValue> elements;

		public ListBackedArray(ManagedObject prototype) {
			super(prototype);
			this.elements = null;
			this.immutable = false;
		}

		public ListBackedArray(ManagedObject prototype, List<ManagedValue> elements, boolean copyOnWrite) {
			super(prototype);
			this.elements = elements;
			this.immutable = copyOnWrite;
		}

		@Override
		public int getLength() {
			if (this.elements == null) return 0;
			return this.elements.size();
		}

		@Override
		public void clear() {
			if (this.elements == null) return;

			if (this.immutable) {
				this.immutable = false;
				this.elements = null;
				return;
			}

			this.elements.clear();
		}

		@Override
		public List<ManagedValue> getElementsReadOnly() {
			if (this.elements == null) return Collections.emptyList();
			return this.elements;
		}

		@Override
		public List<ManagedValue> getElementsMutable() {
			if (this.elements == null) {
				this.elements = new ArrayList<>();
				return this.elements;
			}

			if (this.immutable) {
				this.elements = new ArrayList<>(this.elements);
				this.immutable = false;
			}

			return this.elements;
		}

		@Override
		public ManagedArray makeCopy() {
			if (this.elements == null) {
				return ManagedArray.empty(this.prototype);
			}

			if (this.immutable) {
				return ManagedArray.fromImmutableList(this.prototype, this.elements);
			}

			return ManagedArray.withElements(this.prototype, this.elements);
		}

		@Override
		public ManagedArray makeView(int from, int to) {
			if (this.elements == null) {
				return ManagedArray.empty(this.prototype);
			}

			if (this.immutable) {
				return ManagedArray.fromImmutableList(this.prototype, this.elements.subList(from, to));
			}

			return ManagedArray.withElements(this.prototype, this.elements.subList(from, to));
		}
	}

	public static ManagedArray empty(ManagedObject prototype) {
		return new ListBackedArray(prototype);
	}

	public static ManagedArray withCapacity(ManagedObject prototype, int capacity) {
		return new ListBackedArray(prototype, new ArrayList<>(capacity), false);
	}

	public static ManagedArray withElements(ManagedObject prototype, Collection<ManagedValue> elements) {
		return new ListBackedArray(prototype, new ArrayList<>(elements), false);
	}

	public static ManagedArray fromMutableList(ManagedObject prototype, List<ManagedValue> elements) {
		return new ListBackedArray(prototype, elements, false);
	}

	public static ManagedArray fromImmutableList(ManagedObject prototype, List<ManagedValue> elements) {
		return new ListBackedArray(prototype, elements, true);
	}
}
