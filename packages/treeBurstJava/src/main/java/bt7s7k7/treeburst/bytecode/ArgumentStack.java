package bt7s7k7.treeburst.bytecode;

import java.util.Arrays;

public class ArgumentStack {
	private int[] elements;
	private int length;

	public ArgumentStack() {
		this.length = 0;
		this.elements = new int[16];
	}

	public int size() {
		return this.length;
	}

	public void push(int value) {
		if (this.length == this.elements.length) {
			this.elements = Arrays.copyOf(this.elements, this.elements.length * 2);
		}

		this.elements[this.length++] = value;
	}

	public int pop() {
		return this.elements[--this.length];
	}

	public int peek() {
		return this.elements[this.length - 1];
	}

	public void replace(int value) {
		this.elements[this.length - 1] = value;
	}

	public void increment(int value) {
		this.elements[this.length - 1] += value;
	}
}
