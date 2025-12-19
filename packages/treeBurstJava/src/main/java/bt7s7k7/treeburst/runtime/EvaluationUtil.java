package bt7s7k7.treeburst.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class EvaluationUtil {
	public static ManagedObject getPrototype(ManagedValue value, Scope scope) {
		if (value.equals(Primitive.NULL) || value.equals(Primitive.VOID)) {
			return scope.realm.TablePrototype;
		}

		if (value instanceof Primitive.Number) {
			return scope.realm.NumberPrototype;
		}

		if (value instanceof Primitive.String) {
			return scope.realm.StringPrototype;
		}

		if (value instanceof Primitive.Boolean) {
			return scope.realm.BooleanPrototype;
		}

		if (value instanceof ManagedObject managedObject) {
			return managedObject.prototype;
		}

		throw new IllegalArgumentException("Cannot find prototype of " + value.getClass());
	}

	public static boolean findProperty(ManagedValue receiver, ManagedValue container, String name, Scope scope, ExpressionResult result) {
		if (container instanceof ManagedObject managedObject) {
			return getProperty(receiver, managedObject, name, scope, result);
		}

		if (container instanceof Primitive.String string && name.equals("length")) {
			result.value = Primitive.from(string.value.length());
			return true;
		}

		return getProperty(receiver, getPrototype(container, scope), name, scope, result);
	}

	public static boolean getProperty(ManagedValue receiver, ManagedObject container, String name, Scope scope, ExpressionResult result) {
		var property = container.getOwnProperty(name);
		if (property != null) {
			result.value = property;
			return true;
		}

		if (container.hasGetters) {
			var getter = container.getOwnProperty("get_" + name);

			if (getter != null) {
				if (getter instanceof ManagedFunction getterFunction) {
					evaluateInvocation(receiver, container, getterFunction, Position.INTRINSIC, Collections.emptyList(), scope, result);
					return result.label == null;
				} else {
					result.setException(new Diagnostic("Getter for property '" + name + "' is not a function", Position.INTRINSIC));
					return false;
				}
			}
		}

		var prototype = container.prototype;
		if (prototype != null) {
			return getProperty(receiver, prototype, name, scope, result);
		}

		return false;
	}

	public static boolean setProperty(ManagedObject container, String name, ManagedValue value, Scope scope, ExpressionResult result) {
		if (container instanceof ManagedTable table) {
			var success = table.setOwnProperty(name, value);
			if (success) {
				result.value = value;
				return true;
			}
		}

		if (container.hasSetters) {
			if (getProperty(container, container, "set_" + name, scope, result)) {
				var setter = result.value;

				if (setter instanceof ManagedFunction setterFunction) {
					evaluateInvocation(container, container, setterFunction, Position.INTRINSIC, List.of(value), scope, result);
					return result.label == null;
				} else {
					result.setException(new Diagnostic("Setter for property '" + name + "' is not a function", Position.INTRINSIC));
				}
			}
		}

		return false;
	}

	public static String getValueName(ManagedValue container) {
		if (container == Primitive.VOID) {
			return "void";
		} else if (container == Primitive.NULL) {
			return "null";
		} else if (container instanceof ManagedObject) {
			return container.toString();
		} else {
			return container.getClass().getSimpleName();
		}
	}

	public static void evaluateInvocation(ManagedValue receiver, ManagedValue container, String methodName, Position position, List<ManagedValue> args, Scope scope, ExpressionResult result) {
		evaluateInvocation(receiver, container, Primitive.from(methodName), position, args, scope, result);
	}

	public static void evaluateInvocation(ManagedValue receiver, ManagedValue container, ManagedValue function, Position position, List<ManagedValue> args, Scope scope, ExpressionResult result) {
		if (function instanceof Primitive.String primitiveString) {
			var functionName = primitiveString.value;
			if (!findProperty(container, container, functionName, scope, result)) {
				result.setException(new Diagnostic("Cannot find method \"" + getValueName(container) + "." + functionName + "\"", position));
				return;
			}

			function = result.value;
		}

		if (!(function instanceof ManagedFunction managedFunction)) {
			result.setException(new Diagnostic("Target \"" + getValueName(function) + "\" is not callable", position));
			return;
		}

		if (!managedFunction.getParameterNames().isEmpty() && managedFunction.getParameterNames().get(0).equals("this")) {
			if (args instanceof ArrayList<ManagedValue> mutableArgs) {
				mutableArgs.add(0, receiver);
			} else {
				args = Streams.concat(Stream.of(receiver), args.stream()).toList();
			}
		}

		managedFunction.invoke(args, scope, result);

		var invocationException = result.getExceptionIfPresent();
		if (invocationException != null) {
			result.value = new Diagnostic("While invoking " + managedFunction.toString(), position, List.of(invocationException));
		}
	}
}
