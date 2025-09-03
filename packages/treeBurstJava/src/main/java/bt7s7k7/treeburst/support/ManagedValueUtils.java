package bt7s7k7.treeburst.support;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.runtime.ExpressionEvaluator;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.NativeHandle;
import bt7s7k7.treeburst.runtime.Scope;

public class ManagedValueUtils {
	public static boolean verifyArguments(List<ManagedValue> args, List<String> names, ExpressionResult result) {
		return verifyArguments(names.size(), args, names, result);
	}

	public static boolean verifyArguments(int requiredParameterCount, List<ManagedValue> args, List<String> names, ExpressionResult result) {
		if (args.size() < requiredParameterCount) {
			var argumentErrors = names.stream()
					.limit(requiredParameterCount)
					.skip(args.size())
					.map(name -> new Diagnostic("Missing argument \"" + name + "\"", Position.INTRINSIC))
					.toList();

			result.setException(new Diagnostic("Expected " + names.size() + " arguments, but got " + args.size(), Position.INTRINSIC, argumentErrors));
			return false;
		}

		return true;
	}

	public static List<ManagedValue> ensureArgumentTypes(List<ManagedValue> args, List<String> names, List<Class<?>> types, Scope scope, ExpressionResult result) {
		return ensureArgumentTypes(args, names.size(), names, types, scope, result);
	}

	public static List<ManagedValue> ensureArgumentTypes(List<ManagedValue> args, int requiredParameterCount, List<String> names, List<Class<?>> types, Scope scope, ExpressionResult result) {
		if (types.size() != names.size()) throw new IllegalArgumentException("The lists of argument names and types must be of the same length");

		if (!verifyArguments(requiredParameterCount, args, names, result)) return Collections.emptyList();

		var results = new ArrayList<ManagedValue>();
		var errors = new ArrayList<Diagnostic>();
		argumentLoop: for (int i = 0; i < types.size(); i++) {
			if (i >= args.size()) break;

			var type = types.get(i);
			if (type == ManagedValue.class) {
				results.add(args.get(i));
				continue;
			}

			var value = args.get(i);
			var name = names.get(i);

			// Test if the type is correct. If the requested type is not a subclass of ManagedValue
			// it should be included as a value of a NativeHandle object.
			boolean isInstance;
			if (ManagedValue.class.isAssignableFrom(type)) {
				isInstance = type.isInstance(value);
			} else {
				if (value instanceof NativeHandle nativeHandle) {
					isInstance = type.isInstance(nativeHandle.value);
				} else {
					isInstance = false;
				}
			}

			if (!isInstance) {
				Diagnostic conversionError = null;

				conversion: do {
					ManagedValue convertedValue;

					if (type == Primitive.Boolean.class) {
						convertedValue = ensureBoolean(value, scope, result);
					} else if (type == Primitive.Number.class) {
						convertedValue = ensureNumber(value, scope, result);
					} else if (type == Primitive.String.class) {
						convertedValue = ensureString(value, scope, result);
					} else {
						// No conversion possible
						break conversion;
					}

					if (result.label == null) {
						// Conversion was successful and we can continue to the next argument
						results.add(convertedValue);
						continue argumentLoop;
					}

					var diagnostic = result.getExceptionIfPresent();
					if (diagnostic == null) return results;
					conversionError = diagnostic;
				} while (false);

				errors.add(new Diagnostic(
						"Wrong type for argument \"" + name + "\", expected \"" + type.getSimpleName() + "\", but got \"" + ExpressionEvaluator.getValueName(value) + "\"",
						Position.INTRINSIC,
						conversionError != null ? List.of(conversionError) : null));
			}

			results.add(value);
		}

		if (!errors.isEmpty()) {
			result.label = null;
			var signature = new StringBuilder();
			for (int i = 0; i < names.size(); i++) {
				if (i != 0) signature.append(", ");
				signature.append(names.get(i));
				signature.append(": ");
				var type = types.get(i);
				if (type == ManagedValue.class) {
					signature.append("any");
				} else {
					signature.append(type.getSimpleName());
				}
			}
			result.setException(new Diagnostic("Expected arguments: (" + signature.toString() + ")", Position.INTRINSIC, errors));
		}

		return results;
	}

	@SuppressWarnings("unchecked")
	private static <T extends ManagedValue> T ensureType(ManagedValue value, String operator, Class<T> requestedType, Scope scope, ExpressionResult result) {
		if (requestedType.isInstance(value)) return (T) value;

		evaluateInvocation(value, value, operator, Position.INTRINSIC, Collections.emptyList(), scope, result);
		if (result.label != null) return null;

		value = result.value;
		if (requestedType.isInstance(value)) return (T) value;

		evaluateInvocation(value, scope.globalScope.TablePrototype, operator, Position.INTRINSIC, Collections.emptyList(), scope, result);
		if (result.label != null) return null;

		return requestedType.cast(result.value);
	}

	public static Primitive.Boolean ensureBoolean(ManagedValue value, Scope scope, ExpressionResult result) {
		return ensureType(value, OperatorConstants.OPERATOR_BOOLEAN, Primitive.Boolean.class, scope, result);
	}

	public static Primitive.Number ensureNumber(ManagedValue value, Scope scope, ExpressionResult result) {
		return ensureType(value, OperatorConstants.OPERATOR_NUMBER, Primitive.Number.class, scope, result);
	}

	public static Primitive.String ensureString(ManagedValue value, Scope scope, ExpressionResult result) {
		return ensureType(value, OperatorConstants.OPERATOR_STRING, Primitive.String.class, scope, result);
	}

	public static Expression ensureExpression(ManagedValue value, ExpressionResult result) {
		if (value instanceof NativeHandle handle && handle.value instanceof Expression expression) return expression;
		result.setException(new Diagnostic("Expected expression arguments", Position.INTRINSIC));
		return null;
	}
	public static record BinaryOperatorOperands(ManagedValue left, ManagedValue right) {}

	public static final List<String> BINARY_OPERATOR_PARAMETERS = List.of("this", "left", "right?");

	public static BinaryOperatorOperands prepareBinaryOperator(String name, Class<?> leftType, Class<?> rightType, List<ManagedValue> args, Scope scope, ExpressionResult result) {
		if (args.size() > 2) {
			args = ensureArgumentTypes(args, List.of("this", "left", "right"), List.of(ManagedValue.class, leftType, rightType), scope, result);
			if (result.label != null) return null;

			return new BinaryOperatorOperands(args.get(1), args.get(2));
		}

		args = ensureArgumentTypes(args, List.of("this", "right"), List.of(leftType, rightType), scope, result);
		if (result.label != null) {
			// Failed to cast arguments, which means this overload does not match and we
			// should try to execute this operator using the right operand's handler.
			result.label = null;
			var self = args.get(0);
			var right = args.get(1);
			evaluateInvocation(right, right, name, Position.INTRINSIC, List.of(self, right), scope, result);
			return null;
		}

		return new BinaryOperatorOperands(args.get(0), args.get(1));
	}
}
