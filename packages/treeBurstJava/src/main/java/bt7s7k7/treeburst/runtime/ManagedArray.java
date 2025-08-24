package bt7s7k7.treeburst.runtime;

import java.util.ArrayList;
import java.util.List;

import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class ManagedArray extends ManagedObject {

	public final ArrayList<ManagedValue> elements;

	public ManagedArray(ManagedObject prototype, ArrayList<ManagedValue> elements) {
		super(prototype);
		this.elements = elements;
	}

	@SuppressWarnings("unchecked") // Type is guarded by argument
	public ManagedArray(ManagedObject prototype, List<ManagedValue> elements) {
		this(prototype, elements instanceof ArrayList array ? array : new ArrayList<>(elements));
	}

	public int normalizeIndex(int index, ExpressionResult result) {
		if (index < 0) {
			index = this.elements.size() + index;
		}

		if (index < 0 || index >= this.elements.size()) {
			result.value = new Diagnostic("Index " + index + " out of range of array of length " + this.elements.size(), Position.INTRINSIC);
			result.label = ExpressionResult.LABEL_EXCEPTION;
			return 0;
		}

		return index;
	}

	public int normalizeLimit(int limit, ExpressionResult result) {
		if (limit < 0) {
			limit = this.elements.size() + limit;
		}

		if (limit < 0 || limit > this.elements.size()) {
			result.value = new Diagnostic("Index " + limit + " out of range of array of length " + this.elements.size(), Position.INTRINSIC);
			result.label = ExpressionResult.LABEL_EXCEPTION;
			return 0;
		}

		return limit;
	}

	@Override
	public boolean getProperty(String name, ExpressionResult result) {
		if ("length".equals(name)) {
			result.value = Primitive.from(this.elements.size());
			return true;
		}

		return super.getProperty(name, result);
	}
}
