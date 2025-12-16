package bt7s7k7.treeburst.support;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateExpression;

import java.util.List;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.Scope;
import bt7s7k7.treeburst.runtime.Variable;

public class Parameter {
	public final Position position;
	public final String name;
	public final boolean isDeclaration;
	public final boolean isSpread;
	public final Expression defaultValue;

	public Parameter(Position position, String name, boolean isDeclaration, boolean isSpread, Expression defaultValue) {
		this.position = position;
		this.name = name;
		this.isDeclaration = isDeclaration;
		this.isSpread = isSpread;
		this.defaultValue = defaultValue;
	}

	public Parameter(String name) {
		this(Position.INTRINSIC, name, false, false, null);
	}

	public Parameter(Position position, String name) {
		this(position, name, false, false, null);
	}

	public static final Parameter parse(Expression expression) {
		Expression defaultValue = null;
		var isSpread = false;

		// A parameter can be a rest parameter or have a default value, but not both
		if (expression instanceof Expression.Spread spread) {
			isSpread = true;
			expression = spread.target();
		} else if (expression instanceof Expression.Assignment assignment) {
			defaultValue = assignment.value();
			expression = assignment.receiver();
		}

		boolean isDeclaration;
		String name;
		Position position;

		if (expression instanceof Expression.Identifier identifier) {
			isDeclaration = false;
			name = identifier.name();
			position = identifier.position();
		} else if (expression instanceof Expression.VariableDeclaration declaration && declaration.declaration() instanceof Expression.Identifier identifier) {
			isDeclaration = true;
			name = identifier.name();
			position = identifier.position();
		} else {
			return null;
		}

		return new Parameter(position, name, isDeclaration, isSpread, defaultValue);
	}

	public static void destructure(List<Parameter> parameters, boolean implicitDeclaration, List<ManagedValue> inputs, Scope scope, ExpressionResult result) {
		var inputIndex = 0;

		for (int i = 0; i < parameters.size(); i++) {
			var parameter = parameters.get(i);

			Variable variable = null;
			if (parameter.name.equals("_")) {
				// This is a discard parameter, no variable will be set
			} else if (parameter.isDeclaration) {
				variable = scope.declareVariable(parameter.name);

				if (variable == null) {
					result.setException(new Diagnostic("Duplicate variable declaration of '" + parameter.name + "'", parameter.position));
					return;
				}
			} else {
				if (implicitDeclaration) {
					variable = scope.declareVariable(parameter.name);
				}

				if (variable == null) {
					variable = scope.findVariable(parameter.name);
				}

				if (variable == null) {
					result.setException(new Diagnostic("Cannot find variable '" + parameter.name + "'", parameter.position));
					return;
				}
			}

			if (parameter.isSpread) {
				if (parameters.size() == 1) {
					if (variable == null) continue;
					variable.value = ManagedArray.withElements(scope.globalScope.ArrayPrototype, inputs);
					continue;
				}

				// The count of inputs to consume is the remaining count of arguments not consumed
				// by other parameters. Subtract 1 from total parameters to account for this parameter.
				var inputsToConsume = inputs.size() - (parameters.size() - 1);
				if (inputsToConsume <= 0) {
					if (variable == null) continue;
					variable.value = ManagedArray.empty(scope.globalScope.ArrayPrototype);
					continue;
				}

				var consumed = ManagedArray.withCapacity(scope.globalScope.ArrayPrototype, inputsToConsume);
				var consumedElements = consumed.getElementsMutable();
				var maxIndex = inputIndex + inputsToConsume;

				if (variable == null) {
					inputIndex = maxIndex - 1;
					continue;
				}

				while (inputIndex < maxIndex) {
					consumedElements.add(inputs.get(inputIndex++));
				}

				variable.value = consumed;
				continue;
			}

			var value = inputIndex < inputs.size() ? inputs.get(inputIndex++) : Primitive.VOID;

			if (value == Primitive.VOID && parameter.defaultValue != null) {
				evaluateExpression(parameter.defaultValue, scope, result);
				if (result.label != null) return;

				if (variable == null) continue;
				variable.value = result.value;
				continue;
			}

			if (variable == null) continue;
			variable.value = value;
		}
	}
}
