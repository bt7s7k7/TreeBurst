package bt7s7k7.treeburst.runtime;

import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;

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

	private static List<String> getParametersWithoutOptional(List<String> parameters) {
		var firstOptionalParameter = -1;
		for (int i = 0; i < parameters.size(); i++) {
			var parameter = parameters.get(i);
			if (parameter.endsWith("?")) {
				firstOptionalParameter = i;
				break;
			}
		}

		if (firstOptionalParameter != -1) {
			return parameters.subList(firstOptionalParameter, parameters.size());
		} else {
			return parameters;
		}
	}

	public static NativeFunction simple(GlobalScope globalScope, List<String> parameters, Handler handler) {
		var parametersWithoutOptional = getParametersWithoutOptional(parameters);

		return new NativeFunction(globalScope.FunctionPrototype, parameters, (args, scope, result) -> {
			if (!ManagedValueUtils.verifyArguments(args, parametersWithoutOptional, result)) {
				return;
			}

			if (args.size() > parameters.size()) {
				result.value = new Diagnostic("Too many arguments, expected " + parameters.size() + ", but got " + args.size(), Position.INTRINSIC);
				result.label = ExpressionResult.LABEL_EXCEPTION;
				return;
			}

			handler.handle(args, scope, result);
		});
	}

	public static NativeFunction simple(GlobalScope globalScope, List<String> parameters, List<Class<? extends ManagedValue>> types, Handler handler) {
		return new NativeFunction(globalScope.FunctionPrototype, parameters, (args, scope, result) -> {
			args = ensureArgumentTypes(args, parameters, types, scope, result);
			if (result.label != null) return;

			if (args.size() > parameters.size()) {
				result.value = new Diagnostic("Too many arguments, expected " + parameters.size() + ", but got " + args.size(), Position.INTRINSIC);
				result.label = ExpressionResult.LABEL_EXCEPTION;
				return;
			}

			handler.handle(args, scope, result);
		});
	}
}
