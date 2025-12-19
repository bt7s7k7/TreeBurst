package bt7s7k7.treeburst.bytecode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import bt7s7k7.treeburst.support.ManagedValue;

public final class ValueStack {
	private ManagedValue[] elements;
	private int length;

	public ValueStack() {
		this.length = 0;
		this.elements = new ManagedValue[16];
	}

	public int size() {
		return this.length;
	}

	public void push(ManagedValue value) {
		if (this.length == this.elements.length) {
			this.elements = Arrays.copyOf(this.elements, this.elements.length * 2);
		}

		this.elements[this.length++] = value;
	}

	public void pushAll(List<ManagedValue> values) {
		if (values == null || values.isEmpty()) {
			return;
		}

		var count = values.size();
		var requiredCapacity = this.length + count;
		var targetCapacity = this.elements.length;

		while (requiredCapacity > targetCapacity) {
			targetCapacity *= 2;
		}

		if (this.elements.length != targetCapacity) {
			this.elements = Arrays.copyOf(this.elements, targetCapacity);
		}

		for (var value : values) {
			this.elements[this.length++] = value;
		}
	}

	public ManagedValue pop() {
		ManagedValue value = this.elements[--this.length];
		this.elements[this.length] = null;
		return value;
	}

	public List<ManagedValue> popArguments(int length) {
		if (length == 0) return Collections.emptyList();

		var result = Arrays.asList(this.elements).subList(this.length - length, this.length);
		this.length -= length;
		return result;
	}

	public List<ManagedValue> peekArguments(int length) {
		if (length == 0) return Collections.emptyList();

		var result = Arrays.asList(this.elements).subList(this.length - length, this.length);
		return result;
	}

	public ManagedValue peek() {
		return this.elements[this.length - 1];
	}

	public ManagedValue peek(int offset) {
		return this.elements[this.length - offset - 1];
	}
}
