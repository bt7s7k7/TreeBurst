package bt7s7k7.treeburst.support;

import java.util.Collections;
import java.util.List;

/**
 * Error message produced by the parser containing a position.
 */
public class Diagnostic extends ManagedValue {
	public final String message;
	public final Position position;
	public final List<Diagnostic> additionalErrors;

	public Diagnostic(String message, Position position) {
		this(message, position, null);
	}

	public Diagnostic(String message, Position position, List<Diagnostic> additionalErrors) {
		this.message = message;
		this.position = position;
		this.additionalErrors = additionalErrors == null ? Collections.emptyList() : additionalErrors;
	}

	public String format() {
		return this.format("");
	}

	public String format(String indent) {
		if (this.additionalErrors != null && this.additionalErrors.size() != 0) {
			var result = new StringBuilder();
			this._formatRecursive(result, indent);
			return result.toString();
		}

		return this.position.format(this.message, indent);
	}

	protected void _formatRecursive(StringBuilder target, String indent) {
		target.append(this.position.format(this.message, indent));

		for (var additionalError : this.additionalErrors) {
			target.append("\n"); // Add a newline before the next error
			additionalError._formatRecursive(target, indent + "  ");
		}
	}
}
