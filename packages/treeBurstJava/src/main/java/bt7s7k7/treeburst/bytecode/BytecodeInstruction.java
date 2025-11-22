package bt7s7k7.treeburst.bytecode;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateExpression;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.findProperty;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.getValueName;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_RETURN;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.ManagedFunction;
import bt7s7k7.treeburst.runtime.Scope;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValueUtils;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public interface BytecodeInstruction {
	public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result);

	public static final int STATUS_NORMAL = -1;
	public static final int STATUS_BREAK = -2;

	public static String format(List<BytecodeInstruction> instructions, Map<String, Integer> labels) {
		var result = new StringBuilder();
		var labelPoints = labels.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

		for (int i = 0; i <= instructions.size(); i++) {
			var label = labelPoints.get(i);
			if (label != null) {
				result.append("==== ");
				result.append(label);
				result.append(" ====\n");
			}

			if (i == instructions.size()) break;

			result.append(instructions.get(i));
			result.append("\n");
		}

		return result.toString();
	}

	public static class PrepareInvoke implements BytecodeInstruction {
		public final int argumentCount;
		public final String method;
		public final Position position;

		public PrepareInvoke(int argumentCount, String method, Position position) {
			this.argumentCount = argumentCount;
			this.method = method;
			this.position = position;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			arguments.push(this.argumentCount);

			var value = values.pop();
			var receiver = Primitive.VOID;

			if (this.method != null) {
				receiver = value;

				if (!findProperty(receiver, receiver, this.method, scope, result)) {
					result.setException(new Diagnostic("Cannot find method \"" + getValueName(receiver) + "." + this.method + "\"", this.position));
					return STATUS_BREAK;
				}

				value = result.value;
			}

			if (!(value instanceof ManagedFunction function)) {
				result.setException(new Diagnostic("Target \"" + getValueName(value) + "\" is not callable", this.position));
				return STATUS_BREAK;
			}

			values.push(function);

			if (function.hasThisArgument()) {
				arguments.increment(1);
				values.push(receiver);
			}

			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return this.position.format("PrepareInvoke args = " + this.argumentCount + ", method = " + this.method, "");
		}
	}

	public static class SpreadArgument implements BytecodeInstruction {
		public final Position position;

		public SpreadArgument(Position position) {
			this.position = position;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			var value = values.pop();

			if (!(value instanceof ManagedArray array)) {
				result.setException(new Diagnostic("Spread operator must be used on an array", this.position));
				return STATUS_BREAK;
			}

			var elements = array.getElementsReadOnly();
			arguments.increment(elements.size() - 1); // Subtract 1 because we are popping the array
			values.pushAll(elements);

			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return this.position.format("SpreadArgument", "");
		}
	}

	public static class Invoke implements BytecodeInstruction {
		public final Position position;

		public Invoke(Position position) {
			this.position = position;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			var argumentCount = arguments.pop();
			var callArguments = values.getArguments(argumentCount);
			var function = (ManagedFunction) values.pop();

			function.invoke(callArguments, scope, result);

			for (int i = 0; i < callArguments.size(); i++) {
				callArguments.set(i, null);
			}

			values.push(result.value);

			if (result.label != null) {
				return STATUS_BREAK;
			}

			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return this.position.format("Invoke", "");
		}
	}

	public static class InvokeKeywordFallback implements BytecodeInstruction {
		public final Position position;
		public final List<Expression> expressionArguments;

		public InvokeKeywordFallback(Position position, List<Expression> expressionArguments) {
			this.position = position;
			this.expressionArguments = expressionArguments;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			var argumentCount = arguments.pop();
			var callArguments = values.getArguments(argumentCount);
			var function = (ManagedFunction) values.pop();

			var emitter = new BytecodeEmitter(scope);
			Expression receiverExpression = null;
			if (function.hasThisArgument() && !callArguments.isEmpty()) {
				receiverExpression = new Expression.Literal(Position.INTRINSIC, callArguments.get(0));
			}

			var compilationArgs = emitter.prepareArgumentsForCompilationStageKeywordExecution(receiverExpression, this.expressionArguments);
			emitter.nextPosition = this.position;
			function.invoke(compilationArgs, scope, result);
			if (result.label != null) return STATUS_BREAK;
			if (result.value != Primitive.VOID) throw new IllegalStateException("Compilation stage execution of '" + function.toString() + "' returned a value");

			var createdProgram = new ProgramFragment(emitter.build());
			createdProgram.evaluate(scope, result);
			if (result.label != null) return STATUS_BREAK;

			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return this.position.format("InvokeKeywordFallback", "");
		}
	}

	public static class Jump implements BytecodeInstruction {
		public final String label;
		public int index = -1;

		public Jump(String label) {
			this.label = label;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			if (this.index != -1) {
				return this.index;
			}

			result.label = this.label;
			return STATUS_BREAK;
		}

		@Override
		public String toString() {
			return "Jump " + this.label;
		}
	}

	public static class Conditional extends Jump {
		public final boolean expect;
		public final Position position;

		public Conditional(String label, boolean expect, Position position) {
			super(label);
			this.expect = expect;
			this.position = position;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			var predicateResult = values.pop();

			var predicateValue = ManagedValueUtils.ensureBoolean(predicateResult, scope, result).value;
			if (result.label != null) {
				result.setException(new Diagnostic("While executing conditional", this.position));
				return STATUS_BREAK;
			}

			if (predicateValue != this.expect) {
				return STATUS_NORMAL;
			}

			if (this.index != -1) {
				return this.index;
			}

			result.label = this.label;
			return STATUS_BREAK;
		}

		@Override
		public String toString() {
			return "Conditional[" + this.expect + "] " + this.label;
		}
	}

	public static class Return implements BytecodeInstruction {
		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			result.label = LABEL_RETURN;
			result.value = values.pop();
			return STATUS_BREAK;
		}

		@Override
		public String toString() {
			return "Return";
		}
	}

	public static class Discard implements BytecodeInstruction {
		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			values.pop();
			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return "Discard";
		}

		private Discard() {}

		public static final Discard VALUE = new Discard();
	}

	public static class Reflect implements BytecodeInstruction {
		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			throw new IllegalStateException("Called Reflect.executeInstruction, this should be handled directly in ProgramFragment.evaluate");
		}

		@Override
		public String toString() {
			return "Reflect";
		}

		private Reflect() {}

		public static final Reflect VALUE = new Reflect();
	}

	public static class Duplicate implements BytecodeInstruction {
		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			values.push(values.peek());
			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return "Duplicate";
		}

		private Duplicate() {}

		public static final Duplicate VALUE = new Duplicate();
	}

	public static class Get implements BytecodeInstruction {
		public final String name;
		public final Position position;

		public Get(String name, Position position) {
			this.name = name;
			this.position = position;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			var receiver = values.pop();
			if (!findProperty(receiver, receiver, this.name, scope, result)) {
				result.setException(new Diagnostic("Cannot find property \"" + getValueName(receiver) + "." + this.name + "\"", this.position));
				return STATUS_BREAK;
			}

			values.push(result.value);
			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return this.position.format("Get " + this.name, "");
		}
	}

	public static class Load implements BytecodeInstruction {
		public final String name;
		public final Position position;

		public Load(String name, Position position) {
			this.name = name;
			this.position = position;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			var variable = scope.findVariable(this.name);

			if (variable == null) {
				result.setException(new Diagnostic("Cannot find variable \"" + this.name + "\"", this.position));
				return STATUS_BREAK;
			}

			values.push(variable.value);
			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return this.position.format("Load " + this.name, "");
		}
	}

	public static class Store implements BytecodeInstruction {
		public final String name;
		public final Position position;

		public Store(String name, Position position) {
			this.name = name;
			this.position = position;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			var variable = scope.findVariable(this.name);

			if (variable == null) {
				result.setException(new Diagnostic("Cannot find variable \"" + this.name + "\"", this.position));
				return STATUS_BREAK;
			}

			variable.value = values.peek();
			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return this.position.format("Store " + this.name, "");
		}
	}

	public static class Dynamic implements BytecodeInstruction {
		public final Expression expression;

		public Dynamic(Expression expression) {
			this.expression = expression;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			evaluateExpression(this.expression, scope, result);
			if (result.label != null) {
				return STATUS_BREAK;
			}

			values.push(result.value);
			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return this.expression.toString();
		}
	}
}
