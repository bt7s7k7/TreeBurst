package bt7s7k7.treeburst.runtime;

import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_EXCEPTION;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_RETURN;

import java.util.List;

import bt7s7k7.treeburst.bytecode.ProgramFragment;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Parameter;
import bt7s7k7.treeburst.support.Position;

public class ScriptFunction extends ManagedFunction {
	public final ProgramFragment body;
	public final Scope scope;
	public List<Parameter> parameterDeclarations;

	@Override
	public void invoke(List<ManagedValue> args, Scope scope, ExpressionResult result) {
		Scope functionScope = this.scope.makeChild();

		Parameter.destructure(this.getParameters(), true, args, functionScope, result);
		if (result.label != null) return;

		this.body.evaluate(functionScope, result);
		if (result.label == null) return;

		if (LABEL_RETURN.equals(result.label)) {
			result.label = null;
			return;
		}

		if (LABEL_EXCEPTION.equals(result.label)) {
			return;
		}

		var oldLabel = result.label;
		result.label = null;
		result.setException(new Diagnostic("Did not resolve label '" + oldLabel + "' during function execution", Position.INTRINSIC));
	}

	public ScriptFunction(ManagedObject prototype, List<Parameter> parameters, ProgramFragment body, Scope scope) {
		super(prototype, null, parameters);
		this.body = body;
		this.scope = scope;
	}
}
