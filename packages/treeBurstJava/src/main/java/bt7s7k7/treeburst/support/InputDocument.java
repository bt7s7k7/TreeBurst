package bt7s7k7.treeburst.support;

/**
 * Representation of a parsed document.
 */
public class InputDocument {
	public final String path;
	public final String content;
	public final int lineOffset;

	public InputDocument(String path, String content) {
		this(path, content, 0);
	}

	public InputDocument(String path, String content, int lineOffset) {
		this.path = path;
		this.content = content;
		this.lineOffset = lineOffset;
	}

	/**
	 * Represents a cursor position within a document.
	 */
	public static class Cursor {
		public final int line;
		public final int charIndex;
		public final int absoluteIndex;

		public Cursor(int line, int charIndex, int absoluteIndex) {
			this.line = line;
			this.charIndex = charIndex;
			this.absoluteIndex = absoluteIndex;
		}
	}

	public Cursor getCursorAtIndex(int index) {
		var lineStart = this.content.lastIndexOf("\n", (this.content.length() > index && this.content.charAt(index) == '\n') ? index - 1 : index);

		if (lineStart == -1) {
			lineStart = 0;
		} else if (lineStart != 0) {
			lineStart++;
		}

		var charIndex = index - lineStart;
		var line = countOccurrences(this.content.substring(0, lineStart), '\n') + this.lineOffset;
		return new Cursor(line, charIndex, index);
	}

	public String getLineContentAtCursor(Cursor cursor) {
		var lineStart = cursor.absoluteIndex - cursor.charIndex;
		var lineEnd = this.content.indexOf('\n', cursor.absoluteIndex);

		if (lineEnd == -1) {
			lineEnd = this.content.length();
		}

		return this.content.substring(lineStart, lineEnd);
	}

	public Cursor getCursorByLine(int line, int charIndex) {
		String content = this.content;
		int lineStart = findNthOccurrence(content, '\n', line - this.lineOffset);
		if (lineStart == -1) {
			lineStart = 0;
		} else {
			lineStart++;
		}

		int index = lineStart + charIndex;
		return new Cursor(line, charIndex, index);
	}

	/**
	 * Returns a position at the end of the document.
	 */
	public Position getEOF() {
		return new Position(this, this.content.length(), 1);
	}

	/**
	 * Returns a position spanning the whole document.
	 */
	public Position getFullRange() {
		return new Position(this, 0, this.content.length());
	}

	/**
	 * A helper method to count the occurrences of a character in a string.
	 * This replaces the TypeScript `countOccurrences` utility function.
	 */
	private static int countOccurrences(String text, char character) {
		var count = 0;

		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == character) {
				count++;
			}
		}

		return count;
	}

	/**
	 * A helper method to find the index of the nth occurrence of a character.
	 * This replaces the TypeScript `findNthOccurrence` utility function.
	 */
	private static int findNthOccurrence(String text, char character, int n) {
		if (n <= 0) {
			return -1;
		}

		var count = 0;

		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == character) {
				count++;
				if (count == n) {
					return i;
				}
			}
		}

		return -1;
	}
}
