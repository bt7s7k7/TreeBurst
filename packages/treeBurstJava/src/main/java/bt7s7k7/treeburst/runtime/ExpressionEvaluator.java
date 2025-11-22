package bt7s7k7.treeburst.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class ExpressionEvaluator {

	public static List<ManagedValue> evaluateExpressions(List<Expression> children, boolean excludeVoid, Scope scope, ExpressionResult result) {
		List<ManagedValue> results = new ArrayList<>();

		for (Expression child : children) {
			if (child instanceof Expression.Spread spread) {
				evaluateExpression(spread.target(), scope, result);
				if (result.label != null) return null;

				if (!(result.value instanceof ManagedArray array)) {
					result.setException(new Diagnostic("Spread operator must be used on an array", spread.position()));
					return null;
				}

				// No need to check excludeVoid, arrays cannot have void elements
				results.addAll(array.getElementsReadOnly());
				continue;
			}

			evaluateExpression(child, scope, result);

			if (result.label != null) {
				return null;
			}

			if (excludeVoid && result.value == Primitive.VOID) continue;

			results.add(result.value);
		}

		return results;
	}

	public static void evaluateExpressionBlock(List<Expression> children, Scope scope, ExpressionResult result) {
		outer: for (int i = 0; i < children.size(); i++) {
			var child = children.get(i);

			evaluateExpression(child, scope, result);

			if (result.label != null) {
				if (result.label.startsWith("!")) {
					return;
				} else {
					// Try to find the target label, if not found propagate the labelled result upwards
					for (i = 0; i < children.size(); i++) {
						var child_1 = children.get(i);

						if (!(child_1 instanceof Expression.Label label)) continue;
						if (!label.name().equals(result.label)) continue;

						result.label = null;
						// No need to set the index of the found expression because we are mutating
						// the outer loop's i, but we need to decrement it because it will be
						// incremented by the outer loop
						i--;
						continue outer;
					}

					return;
				}
			}
		}
	}

	public static ManagedObject getPrototype(ManagedValue value, Scope scope) {
		if (value.equals(Primitive.NULL) || value.equals(Primitive.VOID)) {
			return scope.globalScope.TablePrototype;
		}

		if (value instanceof Primitive.Number) {
			return scope.globalScope.NumberPrototype;
		}

		if (value instanceof Primitive.String) {
			return scope.globalScope.StringPrototype;
		}

		if (value instanceof Primitive.Boolean) {
			return scope.globalScope.BooleanPrototype;
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

	public static void evaluateDeclaration(Expression declaration, ManagedValue value, Scope scope, ExpressionResult result) {
		if (declaration instanceof Expression.Identifier identifier) {
			Variable variable = scope.declareVariable(identifier.name());
			if (variable == null) {
				result.setException(new Diagnostic("Duplicate declaration of variable \"" + identifier.name() + "\"", identifier.position()));
				return;
			}

			variable.value = value;
			result.value = variable.value;
			return;
		} else if (declaration instanceof Expression.MemberAccess memberAccess) {
			if (value == Primitive.VOID) {
				result.setException(new Diagnostic("Cannot declare table property of type void", declaration.position()));
				return;
			}

			evaluateExpression(memberAccess.receiver(), scope, result);
			if (result.label != null) return;
			var receiver = result.value;

			if (!(receiver instanceof ManagedTable managedTable)) {
				result.setException(new Diagnostic("Cannot declare properties on \"" + getValueName(receiver) + "\"", declaration.position()));
				return;
			}

			if (!managedTable.declareProperty(memberAccess.member(), value)) {
				result.setException(new Diagnostic("Property \"" + memberAccess.member() + "\" is already defined", declaration.position()));
				return;
			}

			result.value = value;
			return;
		} else {
			result.setException(new Diagnostic("Invalid declaration target", declaration.position()));
			return;
		}
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

	public static void evaluateExpression(Expression expression, Scope scope, ExpressionResult result) {
		if (result.executionLimit != Integer.MAX_VALUE) {
			result.executionCounter++;
			if (result.executionCounter > result.executionLimit) {
				throw new ExecutionLimitReachedException("Script execution reached the limit of " + result.executionLimit + " expressions");
			}
		}

		if (expression instanceof Expression.Literal literal) {
			result.value = literal.value();
			return;
		}

		if (expression instanceof Expression.Identifier identifier) {
			var variable = scope.findVariable(identifier.name());

			if (variable == null) {
				result.setException(new Diagnostic("Cannot find variable \"" + identifier.name() + "\"", identifier.position()));
				return;
			}

			result.value = variable.value;
			return;
		}

		if (expression instanceof Expression.VariableDeclaration declaration) {
			evaluateDeclaration(declaration.declaration(), Primitive.VOID, scope, result);
			return;
		}

		if (expression instanceof Expression.Invocation || expression instanceof Expression.Assignment || expression instanceof Expression.AdvancedAssignment) {
			new ExtendedExpressionEvaluation().run(expression, scope, result);
			return;
		}

		if (expression instanceof Expression.Group group) {
			evaluateExpressionBlock(group.children(), scope, result);
			return;
		}

		if (expression instanceof Expression.MemberAccess memberAccess) {
			evaluateExpression(memberAccess.receiver(), scope, result);
			if (result.label != null) return;

			var receiverValue = result.value;
			if (!findProperty(receiverValue, receiverValue, memberAccess.member(), scope, result)) {
				result.setException(new Diagnostic("Cannot find property \"" + getValueName(receiverValue) + "." + memberAccess.member() + "\"", memberAccess.position()));
			}

			return;
		}

		if (expression instanceof Expression.FunctionDeclaration functionDeclaration) {
			result.value = new ScriptFunction(scope.globalScope.FunctionPrototype, functionDeclaration.parameters(), functionDeclaration.body(), scope);
			return;
		}

		if (expression instanceof Expression.ArrayLiteral arrayLiteral) {
			var elements = evaluateExpressions(arrayLiteral.elements(), true, scope, result);
			if (elements == null) return;
			result.value = ManagedArray.fromMutableList(scope.globalScope.ArrayPrototype, elements);
			return;
		}

		if (expression instanceof Expression.MapLiteral mapLiteral) {
			var map = ManagedMap.empty(scope.globalScope.MapPrototype);

			for (var entry : mapLiteral.entries) {
				var keyExpression = entry.getKey();
				var valueExpression = entry.getValue();

				evaluateExpression(keyExpression, scope, result);
				if (result.label != null) {
					return;
				}
				var key = result.value;
				if (key == Primitive.VOID) continue;

				evaluateExpression(valueExpression, scope, result);
				if (result.label != null) {
					return;
				}
				var value = result.value;
				if (value == Primitive.VOID) continue;

				map.entries.put(key, value);
			}

			result.value = map;
			return;
		}

		if (expression instanceof Expression.Label label) {
			var target = label.target();
			if (target == null) return;
			evaluateExpression(target, scope, result);
			return;
		}

		result.setException(new Diagnostic("Expression of type " + expression.getClass().getSimpleName() + " is not valid here", expression.position()));
	}
}
