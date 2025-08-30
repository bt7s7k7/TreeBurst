package bt7s7k7.treeburst.runtime;

import java.util.stream.Stream;

import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class ExpressionResult {
	public ManagedValue value = Primitive.VOID;
	public String label = null;

	public int executionLimit = Integer.MAX_VALUE;
	public int executionCounter = 0;

	public void setException(Diagnostic exception) {
		if (LABEL_EXCEPTION.equals(this.label)) {
			exception = new Diagnostic(exception.message, exception.position, Stream.concat(exception.additionalErrors.stream(), Stream.of(this.terminate())).toList());
		}

		this.value = exception;
		this.label = LABEL_EXCEPTION;
	}

	public Diagnostic terminate() {
		if (this.label == null) return null;

		if (this.value instanceof Diagnostic diagnostic) {
			this.value = null;
			this.label = null;
			return diagnostic;
		} else {
			var diagnostic = new Diagnostic("Evaluation terminated with label: " + this.label, Position.INTRINSIC);
			this.label = null;
			return diagnostic;
		}
	}

	public Diagnostic getExceptionIfPresent() {
		if (LABEL_EXCEPTION.equals(this.label) && this.value instanceof Diagnostic diagnostic) {
			return diagnostic;
		}

		return null;
	}

	public static final String LABEL_RETURN = "!return";
	public static final String LABEL_EXCEPTION = "!exception";
}
