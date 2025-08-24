package bt7s7k7.treeburst.runtime;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateExpression;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_RETURN;

import java.util.List;
import java.util.Objects;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Primitive;

public class ScriptFunction extends ManagedFunction {
	private final Expression body;
	private final Scope scope;

	@Override
	public void invoke(List<ManagedValue> args, Scope scope, ExpressionResult result) {
		Scope functionScope = this.scope.makeChild();

		for (int i = 0; i < this.parameters.size(); i++) {
			String parameter = this.parameters.get(i);
			ManagedValue arg = i < args.size() ? args.get(i) : Primitive.VOID;

			functionScope.declareVariable(parameter).value = arg;
		}

		evaluateExpression(this.body, functionScope, result);
		if (Objects.equals(result.label, LABEL_RETURN)) {
			result.label = null;
		}
	}

	public ScriptFunction(ManagedObject prototype, List<String> parameters, Expression body, Scope scope) {
		super(prototype, parameters);
		this.body = body;
		this.scope = scope;
	}
}
