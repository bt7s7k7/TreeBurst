package bt7s7k7.treeburst.support;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_EXCEPTION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.runtime.ExpressionEvaluator;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.Scope;
import bt7s7k7.treeburst.runtime.UnmanagedHandle;

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
			if (type == ManagedValue.class) {
				results.add(args.get(i));
				continue;
			}

			var value = args.get(i);
			var name = names.get(i);

			if (!type.isInstance(value)) {
				Diagnostic conversionError = null;

				if (type == Primitive.Boolean.class) {
					var convertedValue = ensureBoolean(value, scope, result);
					if (result.label == null) {
						results.add(convertedValue);
						continue;
					}

					if (!Objects.equals(result.label, LABEL_EXCEPTION) || !(result.value instanceof Diagnostic diagnostic)) return results;
					conversionError = diagnostic;
				}

				if (type == Primitive.Number.class) {
					var convertedValue = ensureNumber(value, scope, result);
					if (result.label == null) {
						results.add(convertedValue);
						continue;
					}

					if (!Objects.equals(result.label, LABEL_EXCEPTION) || !(result.value instanceof Diagnostic diagnostic)) return results;
					conversionError = diagnostic;
				}

				errors.add(new Diagnostic(
						"Wrong type for argument \"" + name + "\", expected \"" + type.getSimpleName() + "\", but got \"" + ExpressionEvaluator.getValueName(value) + "\"",
						Position.INTRINSIC,
						conversionError != null ? List.of(conversionError) : null));
			}

			results.add(value);
		}

		if (!errors.isEmpty()) {
			result.value = new Diagnostic("Cannot invoke", Position.INTRINSIC, errors);
			result.label = LABEL_EXCEPTION;
		}

		return results;
	}

	@SuppressWarnings("unchecked")
	private static <T extends ManagedValue> T ensureType(ManagedValue value, String operator, Class<T> requestedType, Scope scope, ExpressionResult result) {
		if (requestedType.isInstance(value)) return (T) value;

		evaluateInvocation(value, value, operator, Position.INTRINSIC, Collections.emptyList(), scope, result);
		if (result.label != null) return null;

		if (requestedType.isInstance(value)) return (T) value;

		evaluateInvocation(result.value, scope.globalScope.TablePrototype, operator, Position.INTRINSIC, Collections.emptyList(), scope, result);
		if (result.label != null) return null;

		return requestedType.cast(result.value);
	}

	public static Primitive.Boolean ensureBoolean(ManagedValue value, Scope scope, ExpressionResult result) {
		return ensureType(value, OperatorConstants.OPERATOR_BOOLEAN, Primitive.Boolean.class, scope, result);
	}

	public static Primitive.Number ensureNumber(ManagedValue value, Scope scope, ExpressionResult result) {
		return ensureType(value, OperatorConstants.OPERATOR_NUMBER, Primitive.Number.class, scope, result);
	}

	public static Expression ensureExpression(ManagedValue value, ExpressionResult result) {
		if (value instanceof UnmanagedHandle handle && handle.value instanceof Expression expression) return expression;
		result.value = new Diagnostic("Expected expression arguments", Position.INTRINSIC);
		result.label = LABEL_EXCEPTION;
		return null;
	}
}
