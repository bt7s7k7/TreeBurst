package bt7s7k7.treeburst.runtime;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateDeclaration;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateExpression;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateExpressions;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.findProperty;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.getProperty;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.getValueName;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.setProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import bt7s7k7.treeburst.bytecode.BytecodeInstruction;
import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Parameter;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class ExtendedExpressionEvaluation {
	public Position position;
	public Scope scope;
	public ExpressionResult result;

	public Expression targetExpression;
	public ManagedValue targetValue;

	public String functionName;
	public ManagedValue functionValue;

	public Expression operandExpression;
	public ManagedValue operandValue;

	public List<Expression> argumentExpressions;
	public List<ManagedValue> argumentValues;

	public Predicate<ManagedValue> operandCallback;

	public boolean run(Expression expression, Scope scope, ExpressionResult result) {
		this.position = expression.position();
		this.scope = scope;
		this.result = result;

		if (expression instanceof Expression.Invocation invocation) {
			return this.unpackInvocation(invocation)
					&& this.prepareArguments()
					&& this.executeInvocation();
		}

		if (expression instanceof Expression.Assignment assignment) {
			return this.unpackAssignment(assignment)
					&& this.prepareOperand()
					&& this.assignValue();
		}

		if (expression instanceof Expression.AdvancedAssignment advancedAssignment) {
			var target = advancedAssignment.receiver();
			this.functionName = advancedAssignment.operator();
			this.operandExpression = advancedAssignment.value();

			if (target instanceof Expression.Invocation invocation) {
				if (!(this.unpackInvocation(invocation) && this.prepareArguments())) return false;

				// Because ExpressionEvaluator.evaluateInvocation has an optimization that, if the
				// 'arguments' argument is an instance of ArrayList, will prepend the reference to
				// the receiver to the list directly, instead of creating a copy with the first
				// argument added. This will mutate the list and next time we call an invocation
				// using our argumentValues, the receiver argument will be present. Then
				// evaluateInvocation will add the receiver again, creating an invalid argument
				// list. We create a readonly wrapper, so this optimization is not triggered.
				this.argumentValues = Collections.unmodifiableList(this.argumentValues);

				if (!this.executeInvocation()) return false;
				var oldValue = result.value;

				if (!this.prepareOperand()) return false;

				evaluateInvocation(oldValue, oldValue, advancedAssignment.operator(), this.position, List.of(this.operandValue), scope, result);
				if (result.label != null) return false;

				this.argumentValues = Stream.concat(this.argumentValues.stream(), Stream.of(result.value)).toList();
				return this.executeInvocation();
			}

			if (!(this.prepareAssignmentTo(target, true) && this.prepareOperand())) return false;

			evaluateInvocation(this.targetValue, this.targetValue, advancedAssignment.operator(), this.position, List.of(this.operandValue), scope, result);
			if (result.label != null) return false;

			return this.operandCallback.test(result.value);
		}

		throw new IllegalArgumentException("Unsupported expression type: " + expression.getClass());
	}

	/** Dataflow: () -> (argumentExpressions, targetValue, functionValue, functionName) */
	protected boolean unpackInvocation(Expression.Invocation invocation) {
		this.argumentExpressions = invocation.args();

		var target = invocation.target();

		if (target instanceof Expression.MemberAccess memberAccess) {
			evaluateExpression(memberAccess.receiver(), this.scope, this.result);
			if (this.result.label != null) return false;

			this.targetValue = this.result.value;
			this.functionName = memberAccess.member();
		} else {
			evaluateExpression(target, this.scope, this.result);
			if (this.result.label != null) return false;

			this.targetValue = Primitive.VOID;
			this.functionValue = this.result.value;
			this.functionName = target instanceof Expression.Identifier identifier ? identifier.name() : "";
		}

		return true;
	}

	/** Dataflow: (functionName, argumentExpressions) -> (argumentValues ?? argumentExpressions) */
	public boolean prepareArguments() {
		if (this.functionName.startsWith("@")) {
			// Do nothing
		} else {
			var argValues = evaluateExpressions(this.argumentExpressions, false, this.scope, this.result);
			if (argValues == null) return false;

			this.argumentValues = argValues;
		}

		return true;
	}

	/** Dataflow: (targetValue, functionValue ?? functionName, argumentValues ?? argumentExpressions) -> (result) */
	public boolean executeInvocation() {
		if (this.argumentValues == null) {
			if (this.functionValue == null) {
				if (!findProperty(this.targetValue, this.targetValue, this.functionName, this.scope, this.result)) {
					this.result.setException(new Diagnostic("Cannot find method \"" + getValueName(this.targetValue) + "." + this.functionName + "\"", this.position));
					return false;
				}

				this.functionValue = this.result.value;
			}

			if (!(this.functionValue instanceof ManagedFunction function)) {
				this.result.setException(new Diagnostic("Target \"" + getValueName(this.functionValue) + "\" is not callable", this.position));
				return false;
			}

			var fragment = BytecodeInstruction.InvokeMacroFallback.execute(function, function.hasThisArgument() ? this.targetValue : null, this.argumentExpressions, this.scope, this.result, this.position);
			if (this.result.value == null) return false;
			fragment.evaluate(this.scope, this.result);
			if (this.result.value == null) return false;
			return true;
		}

		if (this.functionValue == null) {
			this.functionValue = Primitive.from(this.functionName);
		}

		evaluateInvocation(this.targetValue, this.targetValue, this.functionValue, this.position, this.argumentValues, this.scope, this.result);
		if (this.result.value == null) return false;
		return true;
	}

	/** Dataflow: () -> (operandExpression, targetValue?, operatorCallback) */
	public boolean unpackAssignment(Expression.Assignment assignment) {
		var receiver = assignment.receiver();
		this.operandExpression = assignment.value();
		return this.prepareAssignmentTo(receiver, false);
	}

	/** Dataflow: () -> (targetValue?, operatorCallback) */
	public boolean prepareAssignmentTo(Expression receiver, boolean loadTarget) {
		if (receiver instanceof Expression.Identifier identifier) {
			var variable = this.scope.findVariable(identifier.name());
			if (variable == null) {
				this.result.setException(new Diagnostic("Cannot find variable \"" + identifier.name() + "\"", this.position));
				return false;
			}

			if (loadTarget) {
				this.targetValue = variable.value;
			}

			this.operandCallback = v -> {
				variable.value = v;
				return true;
			};

			return true;
		}

		if (receiver instanceof Expression.VariableDeclaration declaration) {
			if (loadTarget) {
				this.result.setException(new Diagnostic("Invalid target for assignment", declaration.position()));
				return false;
			}

			this.operandCallback = v -> {
				evaluateDeclaration(declaration.declaration(), v, this.scope, this.result);
				return this.result.label == null;
			};

			return true;
		}

		if (receiver instanceof Expression.ArrayLiteral arrayLiteral) {
			if (loadTarget) {
				this.result.setException(new Diagnostic("Invalid target for assignment", receiver.position()));
				return false;
			}

			this.operandCallback = v -> {
				if (!(v instanceof ManagedArray array)) {
					this.result.setException(new Diagnostic("Destructuring is only supported for arrays", this.position));
					return false;
				}

				var parameters = new ArrayList<Parameter>(arrayLiteral.elements().size());

				for (var element : arrayLiteral.elements()) {
					var parameter = Parameter.parse(element);

					if (parameter == null) {
						this.result.setException(new Diagnostic("Invalid target for destructuring", element.position()));
						return false;
					}

					parameters.add(parameter);
				}

				Parameter.destructure(parameters, false, array.getElementsReadOnly(), this.scope, this.result);
				return this.result.label == null;
			};

			return true;
		}

		if (receiver instanceof Expression.MemberAccess memberAccess) {
			evaluateExpression(memberAccess.receiver(), this.scope, this.result);
			if (this.result.label != null) {
				return false;
			}

			var receiver_1 = this.result.value;

			if (!(receiver_1 instanceof ManagedObject container)) {
				this.result.setException(new Diagnostic("Cannot set properties on \"" + getValueName(receiver_1) + "\"", memberAccess.position()));
				return false;
			}

			if (loadTarget) {
				if (!getProperty(container, container, memberAccess.member(), this.scope, this.result)) {
					this.result.setException(new Diagnostic("Property \"" + memberAccess.member() + "\" is not defined on \"" + getValueName(receiver_1) + "\"", memberAccess.position()));
					return false;
				}

				this.targetValue = this.result.value;
			}

			this.operandCallback = valueValue -> {
				if (valueValue == Primitive.VOID) {
					this.result.setException(new Diagnostic("Cannot set a table property to void", memberAccess.position()));
					return false;
				}

				if (!setProperty(container, memberAccess.member(), valueValue, this.scope, this.result)) {
					this.result.setException(new Diagnostic("Property \"" + memberAccess.member() + "\" is not defined on \"" + getValueName(receiver_1) + "\"", memberAccess.position()));
					return false;
				}

				return true;
			};

			return true;
		}

		this.result.setException(new Diagnostic("Invalid assignment target", receiver.position()));
		return false;
	}

	/** Dataflow: (functionName, operandExpression) -> (operandValue) */
	public boolean prepareOperand() {
		if (this.functionName != null && this.functionName.startsWith("@")) {
			this.operandValue = new NativeHandle(this.scope.globalScope.TablePrototype, this.operandExpression);
			return true;
		}

		evaluateExpression(this.operandExpression, this.scope, this.result);
		if (this.result.label != null) return false;

		this.operandValue = this.result.value;

		return true;
	}

	/** Dataflow: (operandCallback, operandValue) -> () */
	public boolean assignValue() {
		return this.operandCallback.test(this.operandValue);
	}
}
