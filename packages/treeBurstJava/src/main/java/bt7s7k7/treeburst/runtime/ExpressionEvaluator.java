package bt7s7k7.treeburst.runtime;

import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_EXCEPTION;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class ExpressionEvaluator {

	public static List<ManagedValue> evaluateExpressions(List<Expression> children, Scope scope, ExpressionResult result) {
		List<ManagedValue> results = new ArrayList<>();

		for (Expression child : children) {
			evaluateExpression(child, scope, result);

			if (result.label != null) {
				return null;
			}

			results.add(result.value);
		}

		return results;
	}

	public static void evaluateExpressionBlock(List<Expression> children, Scope scope, ExpressionResult result) {
		for (Expression child : children) {
			evaluateExpression(child, scope, result);

			if (result.label != null) return;
		}
	}

	public static boolean findProperty(ManagedValue receiver, ManagedValue container, String name, Scope scope, ExpressionResult result) {
		if (container.equals(Primitive.NULL) || container.equals(Primitive.VOID)) {
			return findProperty(receiver, scope.globalScope.TablePrototype, name, scope, result);
		}

		if (container instanceof Primitive.Number) {
			return findProperty(receiver, scope.globalScope.NumberPrototype, name, scope, result);
		}

		if (container instanceof Primitive.String) {
			return findProperty(receiver, scope.globalScope.StringPrototype, name, scope, result);
		}

		if (container instanceof Primitive.Boolean) {
			return findProperty(receiver, scope.globalScope.BooleanPrototype, name, scope, result);
		}

		if (container instanceof ManagedObject managedObject) {
			return managedObject.getProperty(name, result);
		}

		return false;
	}

	public static void evaluateDeclaration(Expression declaration, ManagedValue value, Scope scope, ExpressionResult result) {
		if (declaration instanceof Expression.Identifier) {
			Expression.Identifier identifier = (Expression.Identifier) declaration;
			Variable variable = scope.declareVariable(identifier.name());
			if (variable == null) {
				result.value = new Diagnostic("Duplicate declaration of variable \"" + identifier.name() + "\"", identifier.position());
				result.label = LABEL_EXCEPTION;
				return;
			}

			variable.value = value;
			result.value = variable.value;
			if (value instanceof ManagedObject managedObject && managedObject.name == null) {
				managedObject.name = identifier.name();
			}
			return;
		} else if (declaration instanceof Expression.MemberAccess) {
			Expression.MemberAccess memberAccess = (Expression.MemberAccess) declaration;
			evaluateExpression(memberAccess.receiver(), scope, result);
			if (result.label != null) return;
			ManagedValue receiver = result.value;

			if (!(receiver instanceof ManagedTable managedTable)) {
				result.value = new Diagnostic("Cannot declare properties on \"" + getValueName(receiver) + "\"", declaration.position());
				result.label = LABEL_EXCEPTION;
				return;
			}

			if (!managedTable.declareProperty(memberAccess.member(), value)) {
				result.value = new Diagnostic("Property \"" + memberAccess.member() + "\" is already defined", declaration.position());
				result.label = LABEL_EXCEPTION;
				return;
			}

			result.value = value;
			return;
		} else {
			result.value = new Diagnostic("Invalid declaration target", declaration.position());
			result.label = LABEL_EXCEPTION;
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

	public static void evaluateInvocation(ManagedValue receiver, ManagedValue container, ManagedValue function_1, Position position, List<ManagedValue> args, Scope scope, ExpressionResult result) {
		if (function_1 instanceof Primitive.String primitiveString) {
			var functionName = primitiveString.value;
			if (!findProperty(container, container, functionName, scope, result)) {
				result.value = new Diagnostic("Cannot find method \"" + getValueName(container) + "." + functionName + "\"", position);
				result.label = LABEL_EXCEPTION;
				return;
			}

			function_1 = result.value;
		}

		if (!(function_1 instanceof ManagedFunction managedFunction)) {
			result.value = new Diagnostic("Target \"" + getValueName(container) + "\" is not callable", position);
			result.label = LABEL_EXCEPTION;
			return;
		}

		if (!managedFunction.parameters.isEmpty() && managedFunction.parameters.get(0).equals("this")) {
			if (args instanceof ArrayList<ManagedValue> mutableArgs) {
				mutableArgs.add(0, receiver);
			} else {
				args = Streams.concat(Stream.of(receiver), args.stream()).toList();
			}
		}

		managedFunction.invoke(args, scope, result);

		if (Objects.equals(result.label, LABEL_EXCEPTION) && result.value instanceof Diagnostic diagnostic) {
			result.value = new Diagnostic("Cannot invoke " + managedFunction.toString() + "(" + String.join(", ", managedFunction.parameters) + ")", position, List.of(diagnostic));
		}
	}

	public static void evaluateExpression(Expression expression, Scope scope, ExpressionResult result) {
		if (expression instanceof Expression.NumberLiteral literal) {
			result.value = Primitive.from(literal.value());
			return;
		}

		if (expression instanceof Expression.StringLiteral literal) {
			result.value = Primitive.from(literal.value());
			return;
		}

		if (expression instanceof Expression.Identifier identifier) {
			var variable = scope.findVariable(identifier.name());

			if (variable == null) {
				result.value = new Diagnostic("Cannot find variable \"" + identifier.name() + "\"", identifier.position());
				result.label = LABEL_EXCEPTION;
				return;
			}

			result.value = variable.value;
			return;
		}

		if (expression instanceof Expression.VariableDeclaration declaration) {
			evaluateDeclaration(declaration.declaration(), Primitive.VOID, scope, result);
			return;
		}

		if (expression instanceof Expression.Invocation invocation) {
			var target = invocation.target();
			var args = invocation.args();
			var position = invocation.position();

			ManagedValue receiverValue;
			ManagedValue functionValue;
			String functionName;

			if (target instanceof Expression.MemberAccess memberAccess) {
				evaluateExpression(memberAccess.receiver(), scope, result);
				if (result.label != null) return;

				receiverValue = result.value;
				functionValue = new Primitive.String(memberAccess.member());
				functionName = memberAccess.member();
			} else {
				evaluateExpression(target, scope, result);
				if (result.label != null) return;

				receiverValue = Primitive.VOID;
				functionValue = result.value;
				functionName = target instanceof Expression.Identifier identifier ? identifier.name() : "";
			}

			if (functionName.startsWith("@")) {
				var mappedArgs = new ArrayList<ManagedValue>();

				for (var v : args) {
					mappedArgs.add(new UnmanagedHandle(scope.globalScope.TablePrototype, v));
				}

				evaluateInvocation(receiverValue, receiverValue, functionValue, position, mappedArgs, scope, result);
			} else {
				var argValues = evaluateExpressions(args, scope, result);
				if (argValues == null) return;

				evaluateInvocation(receiverValue, receiverValue, functionValue, position, argValues, scope, result);
			}
			return;
		}

		if (expression instanceof Expression.Assignment assignment) {
			var receiver = assignment.receiver();
			var value = assignment.value();
			var position = assignment.position();

			evaluateExpression(value, scope, result);
			if (result.label != null) return;

			var valueValue = result.value;

			if (receiver instanceof Expression.Identifier identifier) {
				var variable = scope.findVariable(identifier.name());
				if (variable == null) {
					result.value = new Diagnostic("Cannot find variable \"" + identifier.name() + "\"", position);
					result.label = LABEL_EXCEPTION;
					return;
				}

				variable.value = valueValue;
				return;
			} else if (receiver instanceof Expression.VariableDeclaration declaration) {
				evaluateDeclaration(declaration.declaration(), valueValue, scope, result);
				return;
			} else if (receiver instanceof Expression.MemberAccess memberAccess) {
				evaluateExpression(memberAccess.receiver(), scope, result);
				if (result.label != null) {
					return;
				}

				ManagedValue receiver_1 = result.value;

				if (!(receiver_1 instanceof ManagedTable managedTable)) {
					result.value = new Diagnostic("Cannot set properties on \"" + getValueName(receiver_1) + "\"", memberAccess.position());
					result.label = LABEL_EXCEPTION;
					return;
				}

				if (!managedTable.setProperty(memberAccess.member(), valueValue)) {
					result.value = new Diagnostic("Property \"" + memberAccess.member() + "\" is not defined on \"" + getValueName(receiver_1) + "\"", memberAccess.position());
					result.label = LABEL_EXCEPTION;
					return;
				}

				result.value = valueValue;
				return;
			} else {
				result.value = new Diagnostic("Invalid assignment target", receiver.position());
				result.label = LABEL_EXCEPTION;
				return;
			}
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
				result.value = new Diagnostic("Cannot find property \"" + getValueName(receiverValue) + "." + memberAccess.member() + "\"", memberAccess.position());
				result.label = LABEL_EXCEPTION;
				return;
			}

			return;
		}

		if (expression instanceof Expression.FunctionDeclaration functionDeclaration) {
			result.value = new ScriptFunction(scope.globalScope.FunctionPrototype, functionDeclaration.parameters(), functionDeclaration.body(), scope);
			return;
		}

		if (expression instanceof Expression.ArrayLiteral arrayLiteral) {
			var elements = evaluateExpressions(arrayLiteral.elements(), scope, result);
			if (elements == null) return;
			result.value = new ManagedArray(scope.globalScope.ArrayPrototype, elements);
			return;
		}

		if (expression instanceof Expression.MapLiteral mapLiteral) {
			var map = new ManagedMap(scope.globalScope.MapPrototype);

			for (var entry : mapLiteral.entries) {
				var keyExpression = entry.getKey();
				var valueExpression = entry.getValue();

				evaluateExpression(keyExpression, scope, result);
				if (result.label != null) {
					return;
				}
				var key = result.value;

				evaluateExpression(valueExpression, scope, result);
				if (result.label != null) {
					return;
				}
				var value = result.value;

				map.entries.put(key, value);
			}

			result.value = map;
			return;
		}

		throw new IllegalStateException();
	}
}
