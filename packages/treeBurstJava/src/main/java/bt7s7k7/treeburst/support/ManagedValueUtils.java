package bt7s7k7.treeburst.support;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_EXCEPTION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.runtime.ExpressionEvaluator;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.Scope;

public class ManagedValueUtils {
	public static boolean verifyArguments(List<ManagedValue> args, List<String> names, ExpressionResult result) {
		if (args.size() < names.size()) {
			var argumentErrors = names.stream()
					.skip(args.size())
					.map(name -> new Diagnostic("Missing argument \"" + name + "\"", Position.INTRINSIC))
					.toList();

			result.value = new Diagnostic("Expected " + names.size() + " arguments, but got " + args.size(), Position.INTRINSIC, argumentErrors);
			result.label = LABEL_EXCEPTION;
			return false;
		}

		return true;
	}

	public static List<ManagedValue> ensureArgumentTypes(List<ManagedValue> args, List<String> names, List<Class<? extends ManagedValue>> types, Scope scope, ExpressionResult result) {
		if (types.size() != names.size()) throw new IllegalArgumentException("The lists of argument names and types must be of the same length");

		if (!verifyArguments(args, names, result)) return Collections.emptyList();

		var results = new ArrayList<ManagedValue>();
		var errors = new ArrayList<Diagnostic>();
		for (int i = 0; i < types.size(); i++) {
			var type = types.get(i);
			if (type == null) {
				results.set(i, args.get(i));
				continue;
			}

			var value = args.get(i);
			var name = names.get(i);

			if (!type.isInstance(value)) {
				Diagnostic conversionError = null;

				errors.add(new Diagnostic(
						"Wrong type for argument \"" + name + "\", expected \"" + type.getSimpleName() + "\", but got \"" + ExpressionEvaluator.getValueName(value) + "\"",
						Position.INTRINSIC,
						conversionError != null ? List.of(conversionError) : null));
			}

			results.set(i, value);
		}

		if (!errors.isEmpty()) {
			result.value = new Diagnostic("Cannot invoke", Position.INTRINSIC, errors);
			result.label = LABEL_EXCEPTION;
		}

		return results;
	}

	@SuppressWarnings("unchecked")
	private static <T extends ManagedValue> T ensureType(ManagedValue value, Class<T> requestedType, Scope scope, ExpressionResult result) {
		if (requestedType.isInstance(value)) return (T) value;

		evaluateInvocation(value, value, OperatorConstants.OPERATOR_BOOLEAN, Position.INTRINSIC, Collections.emptyList(), scope, result);
		if (result.label != null) return null;

		if (requestedType.isInstance(value)) return (T) value;

		evaluateInvocation(result.value, scope.globalScope.TablePrototype, OperatorConstants.OPERATOR_BOOLEAN, Position.INTRINSIC, Collections.emptyList(), scope, result);
		if (result.label != null) return null;

		return requestedType.cast(result.value);
	}

	public static Primitive.Boolean ensureBoolean(ManagedValue value, Scope scope, ExpressionResult result) {
		return ensureType(value, Primitive.Boolean.class, scope, result);
	}

	public static Primitive.Number ensureNumber(ManagedValue value, Scope scope, ExpressionResult result) {
		return ensureType(value, Primitive.Number.class, scope, result);
	}
}
