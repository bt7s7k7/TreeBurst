package bt7s7k7.treeburst.bytecode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.ManagedFunction;
import bt7s7k7.treeburst.runtime.NativeHandle;
import bt7s7k7.treeburst.runtime.Scope;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Parameter;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class BytecodeEmitter {
	public class CompilationScope extends Scope {
		public BytecodeEmitter getEmitter() {
			return BytecodeEmitter.this;
		}
	}

	public final Scope scope;
	public Position nextPosition;

	public BytecodeEmitter(Scope scope) {
		this.scope = scope;
	}

	protected int nextId = 0;
	protected List<BytecodeInstruction> instructions = new ArrayList<>();
	protected Map<String, Integer> labels = new HashMap<>();

	public String getNextLabel() {
		return "_" + this.nextId++;
	}

	public void emit(BytecodeInstruction instruction) {
		this.instructions.add(instruction);
	}

	public void label(String label) {
		this.labels.put(label, this.instructions.size());
	}

	public void compileBlock(List<Expression> children, ExpressionResult result) {
		for (var child : children) {
			if (child instanceof Expression.Spread spread) {
				this.compile(spread.target(), result);
				if (result.label != null) return;

				this.emit(new BytecodeInstruction.SpreadArgument(spread.position()));
				continue;
			}

			this.compile(child, result);
			if (result.label != null) return;
		}
	}

	public void emitDeclaration(Expression declaration, Consumer<ExpressionResult> valueEmitter, ExpressionResult result) {
		if (declaration instanceof Expression.Identifier identifier) {
			valueEmitter.accept(result);
			if (result.label != null) return;
			this.emit(new BytecodeInstruction.Declare(identifier.name(), identifier.position()));
			return;
		}

		if (declaration instanceof Expression.MemberAccess memberAccess) {
			this.compile(memberAccess.receiver(), result);
			if (result.label != null) return;
			valueEmitter.accept(result);
			if (result.label != null) return;
			this.emit(new BytecodeInstruction.DeclareProperty(memberAccess.member(), memberAccess.position()));
			return;
		}

		result.setException(new Diagnostic("Invalid declaration target", declaration.position()));
		return;
	}

	public List<Parameter> parseParameters(Expression.ArrayLiteral arrayLiteral, ExpressionResult result) {
		var parameters = new ArrayList<Parameter>(arrayLiteral.elements().size());

		for (var element : arrayLiteral.elements()) {
			var parameter = Parameter.parse(element);

			if (parameter == null) {
				result.setException(new Diagnostic("Invalid target for destructuring", element.position()));
				return null;
			}

			parameters.add(parameter);
		}

		return parameters;
	}

	public void emitInvocation(Expression.Invocation invocation, ExpressionResult result) {
		if (this.tryCompilationStageMacroExecution(invocation, result)) return;

		String method = null;
		String name = null;

		if (invocation.target() instanceof Expression.MemberAccess memberAccess) {
			this.compile(memberAccess.receiver(), result);
			if (result.label != null) return;

			name = method = memberAccess.member();
		} else {
			if (invocation.target() instanceof Expression.Identifier identifier) {
				name = identifier.name();
			}

			this.compile(invocation.target(), result);
			if (result.label != null) return;
		}

		var isMacro = name != null && name.startsWith("@");

		var argumentCount = invocation.args().size();

		if (isMacro) argumentCount = 0;

		this.emit(new BytecodeInstruction.PrepareInvoke(argumentCount, method, invocation.position()));

		if (!isMacro) {
			this.compileBlock(invocation.args(), result);
			if (result.label != null) return;
		}

		if (isMacro) {
			this.emit(new BytecodeInstruction.InvokeMacroFallback(invocation.position(), invocation.args()));
		} else {
			this.emit(new BytecodeInstruction.Invoke(invocation.position()));
		}
	}

	public void compile(Expression expression, ExpressionResult result) {
		if (expression instanceof Expression.Literal literal) {
			this.emit(literal.value());
			return;
		}

		if (expression instanceof Expression.Identifier identifier) {
			this.emit(new BytecodeInstruction.Load(identifier.name(), identifier.position()));
			return;
		}

		if (expression instanceof Expression.Invocation invocation) {
			this.emitInvocation(invocation, result);
			return;
		}

		if (expression instanceof Expression.VariableDeclaration declaration) {
			this.emitDeclaration(declaration.declaration(), __ -> this.emit(Primitive.VOID), result);
			return;
		}

		if (expression instanceof Expression.Assignment assignment) {
			var receiver = assignment.receiver();
			var value = assignment.value();

			if (receiver instanceof Expression.Identifier identifier) {
				this.compile(value, result);
				if (result.label != null) return;
				this.emit(new BytecodeInstruction.Store(identifier.name(), identifier.position()));
				return;
			}

			if (receiver instanceof Expression.VariableDeclaration declaration) {
				this.emitDeclaration(declaration.declaration(), result_1 -> this.compile(value, result_1), result);
				return;
			}

			if (receiver instanceof Expression.ArrayLiteral arrayLiteral) {
				var parameters = this.parseParameters(arrayLiteral, result);
				if (result.label != null) return;

				this.compile(value, result);
				if (result.label != null) return;

				this.emit(new BytecodeInstruction.Destructure(parameters, arrayLiteral.position()));
				return;
			}

			if (receiver instanceof Expression.MemberAccess memberAccess) {
				this.compile(memberAccess.receiver(), result);
				if (result.label != null) return;

				this.compile(value, result);
				if (result.label != null) return;

				this.emit(new BytecodeInstruction.Set(memberAccess.member(), memberAccess.position()));
				return;
			}

			result.setException(new Diagnostic("Invalid assignment target", receiver.position()));
			return;
		}

		if (expression instanceof Expression.AdvancedAssignment assignment) {
			var receiver = assignment.receiver();
			var value = assignment.value();

			if (receiver instanceof Expression.Identifier identifier) {
				// Read the variable, execute the operator
				var invocation = Expression.Invocation.makeMethodCall(assignment.position(), identifier, assignment.operator(), List.of(value));
				this.compile(invocation, result);
				if (result.label != null) return;
				// Store the result into the variable
				this.emit(new BytecodeInstruction.Store(identifier.name(), identifier.position()));
				return;
			}

			if (receiver instanceof Expression.MemberAccess memberAccess) {
				// Evaluate and cache the receiver (e.g., 'obj'). We duplicate it on the stack so it
				// can be reused for both the initial read and the subsequent write, avoiding
				// re-evaluation.
				this.compile(memberAccess.receiver(), result);
				if (result.label != null) return;
				this.emit(BytecodeInstruction.Duplicate.VALUE);

				// Perform the operation. We create a synthetic access with an empty receiver to
				// pull the cached value from the stack, then invoke the operator on it.
				var provider = new Expression.MemberAccess(memberAccess.position(), RawInstructions.empty(assignment.position()), memberAccess.member());
				var operatorCall = Expression.Invocation.makeMethodCall(assignment.position(), provider, assignment.operator(), List.of(value));
				this.compile(operatorCall, result);
				if (result.label != null) return;

				// Set the result of the operator call using the receiver already on the stack
				this.emit(new BytecodeInstruction.Set(memberAccess.member(), memberAccess.position()));
				return;
			}

			if (receiver instanceof Expression.Invocation invocation) {
				receiver = invocation.target();
				String method = null;

				if (receiver instanceof Expression.MemberAccess memberAccess) {
					receiver = memberAccess.receiver();
					method = memberAccess.member();
				}

				// The structure of operations is as follows:
				// - evaluate the receiver
				// + start write operation
				// | - evaluate arguments of invocation
				// | + start execute operator
				// | | + start read operation
				// | | | - duplicate the evaluated arguments of invocation
				// | | # invoke
				// | | - evaluate provided operand
				// | # invoke
				// # invoke

				// Prepare the write call. This represents the final call that will update the
				// value, but the last operand (the value) will be added later.
				var executionOfTheInvocation = method == null
						? (new Expression.Invocation(invocation.position(), receiver, invocation.args()))
						: (Expression.Invocation.makeMethodCall(invocation.position(), receiver, method, invocation.args()));

				// Prepare the read call via stack manipulation. We use DuplicateArguments to clone
				// the stack portion containing the receiver and arguments, so they can be used for
				// both reading and writing. The DuplicateArguments duplicates the arguments meant
				// for the write operation, so the offset 1 is used to duplicate 1 less values, i.e.
				// exclude what will be the last argument (new value) which should not be given to
				// the read operation and also does not exist yet.
				var readingTheOldValue = List.of(
						new BytecodeInstruction.DuplicateArguments(1),
						new BytecodeInstruction.Invoke(invocation.position()));

				// Create the operator call, the receiver (first operand) will be the the result of the code above
				var operatorCall = Expression.Invocation.makeMethodCall(assignment.position(), new RawInstructions(assignment.position(), readingTheOldValue), assignment.operator(), List.of(value));

				// Append the operator result back into the original invocation's arguments, completing the write operation
				var consumer = executionOfTheInvocation.withArgument(operatorCall);
				this.compile(consumer, result);
				return;
			}

			result.setException(new Diagnostic("Invalid advanced assignment target", receiver.position()));
			return;
		}

		if (expression instanceof Expression.Group group) {
			var first = true;
			for (var child : group.children()) {
				if (!first) {
					this.emit(BytecodeInstruction.Discard.VALUE);
				} else {
					first = false;
				}

				// A label without a target will not push anything so we shouldn't discard
				if (child instanceof Expression.Label label && label.target() == null) first = true;

				this.compile(child, result);
				if (result.label != null) return;
			}

			// Nothing will be pushed, but all expressions need to push something, so push a constant void
			if (first) this.emit(Primitive.VOID);

			return;
		}

		if (expression instanceof Expression.MemberAccess memberAccess) {
			this.compile(memberAccess.receiver(), result);
			if (result.label != null) return;
			this.emit(new BytecodeInstruction.Get(memberAccess.member(), memberAccess.position()));
			return;
		}

		if (expression instanceof Expression.FunctionDeclaration functionDeclaration) {
			this.emit(new BytecodeInstruction.DeclareFunction(new ProgramFragment(functionDeclaration.body()), functionDeclaration.parameters()));
			return;
		}

		if (expression instanceof Expression.ArrayLiteral arrayLiteral) {
			var elementCount = arrayLiteral.elements().size();
			this.emit(new BytecodeInstruction.PrepareCollectionLiteral(elementCount));

			this.compileBlock(arrayLiteral.elements(), result);
			if (result.label != null) return;

			this.emit(BytecodeInstruction.BuildArray.VALUE);

			return;
		}

		if (expression instanceof Expression.MapLiteral mapLiteral) {
			var entryCount = mapLiteral.entries.size();

			for (var kv : mapLiteral.entries) {
				this.compile(kv.getKey(), result);
				if (result.label != null) return;

				this.compile(kv.getValue(), result);
				if (result.label != null) return;
			}

			this.emit(new BytecodeInstruction.BuildMap(entryCount));

			return;
		}

		if (expression instanceof Expression.Label label) {
			var target = label.target();
			this.label(label.name());
			if (target == null) return;
			this.compile(target, result);
			return;
		}

		if (expression instanceof RawInstructions rawInstructions) {
			for (var instruction : rawInstructions.instructions()) {
				this.emit(instruction);
			}

			return;
		}

		this.emit(new BytecodeInstruction.Dynamic(expression));
	}

	public static record BuildResult(List<BytecodeInstruction> instructions, Map<String, Integer> labels) {}

	public BuildResult build() {
		for (var instruction : this.instructions) {
			if (instruction instanceof BytecodeInstruction.Jump goto_1) {
				var index = this.labels.get(goto_1.label);
				if (index != null) goto_1.index = (int) index;
				continue;
			}
		}

		return new BuildResult(this.instructions, this.labels);
	}

	public boolean tryCompilationStageMacroExecution(Expression.Invocation invocation, ExpressionResult result) {
		Expression receiver = null;
		ManagedFunction function = null;

		if (invocation.target() instanceof Expression.Identifier staticFunction) {
			var staticName = staticFunction.name();
			if (staticName.startsWith("@")) {
				var functionVariable = this.scope.findVariable(staticName);
				if (functionVariable != null && functionVariable.value instanceof ManagedFunction function_1) {
					function = function_1;
				}
			}
		}

		if (invocation.target() instanceof Expression.MemberAccess memberAccess) {
			var member = memberAccess.member();
			if (member.startsWith("@")) {
				var functionValue = this.scope.globalScope.TablePrototype.getOwnProperty(member);
				if (functionValue != null && functionValue instanceof ManagedFunction function_1) {
					function = function_1;
					receiver = memberAccess.receiver();
				}
			}
		}

		if (function != null) {
			// Execute compilation stage of macro function
			var arguments = this.prepareArgumentsForCompilationStageMacroExecution(receiver, invocation.args());
			this.nextPosition = invocation.position();
			function.invoke(arguments, this.scope, result);

			if (result.label != null) {
				result.setException(new Diagnostic("While executing macro", invocation.position()));
				return true;
			}

			if (result.value != Primitive.VOID) {
				result.setException(new Diagnostic("Compilation stage execution of '" + function.toString() + "' returned a value", invocation.position()));
			}

			return true;
		}

		return false;
	}

	public List<ManagedValue> prepareArgumentsForCompilationStageMacroExecution(Expression receiver, List<Expression> expressions) {
		var arguments = new ArrayList<ManagedValue>(expressions.size() + (receiver != null ? 2 : 1));
		if (receiver != null) arguments.add(new NativeHandle(this.scope.globalScope.TablePrototype, receiver));

		for (var argument : expressions) {
			arguments.add(new NativeHandle(this.scope.globalScope.TablePrototype, argument));
		}

		arguments.add(new NativeHandle(this.scope.globalScope.TablePrototype, this));

		return arguments;
	}

}
