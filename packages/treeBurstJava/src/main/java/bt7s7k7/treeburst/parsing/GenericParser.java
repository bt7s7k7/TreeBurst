package bt7s7k7.treeburst.parsing;

import java.util.List;

public class GenericParser {
	@FunctionalInterface
	public interface TokenPredicate {
		public boolean test(StringSpan input, int index);
	}

	public int index = 0;
	public StringSpan input;

	public GenericParser(StringSpan input) {
		this.input = input;
	}

	public GenericParser(String input) {
		this.input = new StringSpan(input);
	}

	public char getCurrent() {
		return this.input.at(this.index);
	}

	public char at(int offset) {
		if (this.index + offset < this.input.length() && this.index + offset >= 0) {
			return this.input.at(this.index + offset);
		} else {
			return '\0'; // Return null character for out-of-bounds
		}
	}

	public StringSpan read(int length) {
		StringSpan result = this.input.substring(this.index, length);
		this.index += length;
		return result;
	}

	public boolean skipUntil(String token) {
		return skipUntil((input, index) -> input.startsWith(token, index));
	}

	public boolean skipUntil(TokenPredicate predicate) {
		while (this.index < this.input.length()) {
			if (predicate.test(this.input, this.index)) {
				return true;
			}
			this.index++;
		}
		return false;
	}

	public StringSpan readUntil(String token) {
		return readUntil((input, index) -> input.startsWith(token, index));
	}

	public StringSpan readUntil(TokenPredicate predicate) {
		int start = this.index;
		this.skipUntil(predicate);
		int end = this.index;
		return this.input.substring(start, end - start);
	}

	public boolean skipWhile(TokenPredicate predicate) {
		return this.skipUntil((input, index) -> !predicate.test(input, index));
	}

	public StringSpan readWhile(TokenPredicate predicate) {
		return this.readUntil((input, index) -> !predicate.test(input, index));
	}

	public boolean isDone() {
		return this.index >= this.input.length();
	}

	public GenericParser restart(StringSpan input) {
		this.input = input;
		this.index = 0;
		return this;
	}

	public boolean consume(String token) {
		if (this.input.startsWith(token, this.index)) {
			this.index += token.length();
			return true;
		}
		return false;
	}

	public String consume(String[] tokens) {
		for (var element : tokens) {
			if (this.consume(element)) {
				return element;
			}
		}
		return null;
	}

	public boolean matches(String token) {
		return this.input.startsWith(token, this.index);
	}

	public String matches(List<String> tokens) {
		for (var element : tokens) {
			if (this.matches(element)) {
				return element;
			}
		}

		return null;
	}

	public boolean matches(TokenPredicate predicate) {
		return predicate.test(this.input, this.index);
	}
}
