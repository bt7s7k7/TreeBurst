package bt7s7k7.treeburst.runtime;

import java.util.List;

import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.ManagedValueUtils;
import bt7s7k7.treeburst.support.Position;

public class NativeFunction extends ManagedFunction {
	@FunctionalInterface
	public interface Handler {
		void handle(List<ManagedValue> args, Scope scope, ExpressionResult result);
	}

	private final Handler handler;

	public NativeFunction(ManagedObject prototype, List<String> parameters, Handler handler) {
		super(prototype, parameters);
		this.handler = handler;
	}

	@Override
	public void invoke(List<ManagedValue> args, Scope scope, ExpressionResult result) {
		this.handler.handle(args, scope, result);
	}

	public static NativeFunction simple(GlobalScope scope, List<String> parameters, Handler handler) {
		final List<String> parametersWithoutOptional;
		var firstOptionalParameter = -1;
		for (int i = 0; i < parameters.size(); i++) {
			var parameter = parameters.get(i);
			if (parameter.endsWith("?")) {
				firstOptionalParameter = i;
				break;
			}
		}

		if (firstOptionalParameter != -1) {
			parametersWithoutOptional = parameters.subList(firstOptionalParameter, parameters.size());
		} else {
			parametersWithoutOptional = parameters;
		}

		return new NativeFunction(scope.FunctionPrototype, parameters, (args, simpleScope, simpleResult) -> {
			if (!ManagedValueUtils.verifyArguments(args, parametersWithoutOptional, simpleResult)) {
				return;
			}

			if (args.size() > parameters.size()) {
				simpleResult.value = new Diagnostic("Too many arguments, expected " + parameters.size() + ", but got " + args.size(), Position.INTRINSIC);
				simpleResult.label = ExpressionResult.LABEL_EXCEPTION;
				return;
			}

			handler.handle(args, simpleScope, simpleResult);
		});
	}
}
