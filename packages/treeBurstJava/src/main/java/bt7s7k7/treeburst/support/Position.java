package bt7s7k7.treeburst.support;

import java.util.Objects;

public class Position {
	private final InputDocument document;
	private final int index;
	private int length;

	private Integer line = null;
	private Integer charPosition = null;

	private Position() {
		this.document = null;
		this.index = 0;
		this.length = 0;
	}

	public Position(InputDocument document, int index, int length) {
		this.document = Objects.requireNonNull(document);
		this.index = index;
		this.length = length;
	}

	public InputDocument getDocument() {
		return document;
	}

	public int getIndex() {
		return index;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;

		this.line = null;
		this.charPosition = null;
	}

	public Integer getLine() {
		if (this.line == null) {
			resolve();
		}
		return this.line;
	}

	public Integer getChar() {
		if (this.charPosition == null) {
			resolve();
		}
		return this.charPosition;
	}

	public int getOffset() {
		return this.index;
	}

	public int getEnd() {
		return this.index + this.length;
	}

	public Position after() {
		return new Position(this.document, this.index + this.length, 1);
	}

	protected void resolve() {
		if (this.line != null && this.charPosition != null) {
			return;
		}
		var cursor = this.document.getCursorAtIndex(this.index);
		this.line = cursor.line;
		this.charPosition = cursor.charIndex;
	}

	protected String getLocationString() {
		return this.document.path + ":" + (this.getLine() + 1) + ":" + (this.getChar() + 1);
	}

	public String format(String message, String indent) {
		var rawLine = this.document.getLineContentAtCursor(this.document.getCursorAtIndex(this.index));
		var tabOffset = 0;
		while (tabOffset < rawLine.length()) {
			char c = rawLine.charAt(tabOffset);
			if (c == ' ' || c == '\t') {
				tabOffset++;
			} else {
				break;
			}
		}

		var lineContent = rawLine.substring(tabOffset);
		var pointer = " ".repeat(this.getChar() - tabOffset) + (this.length > 1 ? "~".repeat(this.length) : "^");

		var result = new StringBuilder();
		result.append(indent).append(this.getLocationString());

		if (message != null) {
			result.append(" - " + message);
		}

		result.append("\n").append(indent).append(lineContent).append("\n").append(indent).append(pointer);

		return result.toString();
	}

	@Override
	public String toString() {
		return this.getLocationString();
	}

	public static final Position INTRINSIC = new Position() {
		@Override
		protected String getLocationString() {
			return "INTRINSIC";
		}

		@Override
		public String format(String message, String indent) {
			return indent + message;
		}
	};
}
