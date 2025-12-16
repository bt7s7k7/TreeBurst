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

			var argumentCount = invocation.args().size();
			var isMacro = name != null && name.startsWith("@");

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

			this.emit(BytecodeInstruction.BuildArray.INSTANCE);

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
			if (result.label != null) return true;
			if (result.value != Primitive.VOID) throw new IllegalStateException("Compilation stage execution of '" + function.toString() + "' returned a value");
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
