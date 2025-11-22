package bt7s7k7.treeburst.bytecode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.ManagedFunction;
import bt7s7k7.treeburst.runtime.NativeHandle;
import bt7s7k7.treeburst.runtime.Scope;
import bt7s7k7.treeburst.support.ManagedValue;
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
			if (this.tryCompilationStageKeywordExecution(invocation, result)) return;

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
			var isKeyword = name != null && name.startsWith("@");

			if (isKeyword) argumentCount = 0;

			this.emit(new BytecodeInstruction.PrepareInvoke(argumentCount, method, invocation.position()));

			if (!isKeyword) {
				this.compileBlock(invocation.args(), result);
				if (result.label != null) return;
			}

			if (isKeyword) {
				this.emit(new BytecodeInstruction.InvokeKeywordFallback(invocation.position(), invocation.args()));
			} else {
				this.emit(new BytecodeInstruction.Invoke(invocation.position()));
			}

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

				this.compile(child, result);
				if (result.label != null) return;
			}
			return;
		}

		if (expression instanceof Expression.MemberAccess memberAccess) {
			this.compile(memberAccess.receiver(), result);
			if (result.label != null) return;
			this.emit(new BytecodeInstruction.Get(memberAccess.member(), memberAccess.position()));
			return;
		}

		if (expression instanceof Expression.Label label) {
			var target = label.target();
			if (target == null) return;
			this.label(label.name());
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

	public boolean tryCompilationStageKeywordExecution(Expression.Invocation invocation, ExpressionResult result) {
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
			// Execute compilation stage of keyword function
			var arguments = this.prepareArgumentsForCompilationStageKeywordExecution(receiver, invocation.args());
			this.nextPosition = invocation.position();
			function.invoke(arguments, this.scope, result);
			if (result.label != null) return true;
			if (result.value != Primitive.VOID) throw new IllegalStateException("Compilation stage execution of '" + function.toString() + "' returned a value");
			return true;
		}

		return false;
	}

	public List<ManagedValue> prepareArgumentsForCompilationStageKeywordExecution(Expression receiver, List<Expression> expressions) {
		var arguments = new ArrayList<ManagedValue>(expressions.size() + (receiver != null ? 2 : 1));
		if (receiver != null) arguments.add(new NativeHandle(this.scope.globalScope.TablePrototype, receiver));

		for (var argument : expressions) {
			arguments.add(new NativeHandle(this.scope.globalScope.TablePrototype, argument));
		}

		arguments.add(new NativeHandle(this.scope.globalScope.TablePrototype, this));

		return arguments;
	}

}
