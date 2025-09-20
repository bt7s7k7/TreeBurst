package bt7s7k7.treeburst.parsing;

import java.util.Arrays;

public final class StringSpan {
	private final char[] data;
	private final int index;
	private int length;

	public StringSpan(char[] data, int index, int length) {
		this.data = data;
		this.index = index;
		this.length = length;
	}

	public StringSpan(String source) {
		this.data = source.toCharArray();
		this.index = 0;
		this.length = this.data.length;
	}

	public int length() {
		return this.length;
	}

	public int index() {
		return this.index;
	}

	public char at(int index) {
		return this.data[index + this.index];
	}

	public boolean isEmpty() {
		return this.length == 0;
	}

	private static final char[] patternStorage = new char[256];

	public boolean startsWith(String pattern, int index) {
		if (pattern.length() > this.length - index) return false;

		var patternLength = pattern.length();
		pattern.getChars(0, patternLength, patternStorage, 0);
		return Arrays.equals(patternStorage, 0, patternLength, this.data, this.index + index, this.index + index + patternLength);
	}

	public boolean endsWith(String pattern) {
		if (pattern.length() > this.length) return false;

		var patternLength = pattern.length();
		pattern.getChars(0, patternLength, patternStorage, 0);
		return Arrays.equals(patternStorage, 0, patternLength, this.data, this.index + this.length - patternLength, this.index + this.length);
	}

	public StringSpan substring(int start, int length) {
		if (start >= this.length
				|| start + length > this.length
				|| start < 0
				|| length < 0) {
			throw new IndexOutOfBoundsException("Range [" + start + ", " + start + " + " + length + ") is out of bound for length " + this.length);
		}
		return new StringSpan(this.data, this.index + start, length);
	}

	@Override
	public String toString() {
		return String.copyValueOf(this.data, this.index, this.length);
	}
}
