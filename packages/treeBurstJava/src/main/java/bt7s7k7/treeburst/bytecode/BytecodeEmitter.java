package bt7s7k7.treeburst.bytecode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.Scope;
import bt7s7k7.treeburst.support.Primitive;

public class BytecodeEmitter {
	public class CompilationScope extends Scope {
		public BytecodeEmitter getEmitter() {
			return BytecodeEmitter.this;
		}
	}

	public final Scope scope;

	public BytecodeEmitter(Scope scope) {
		this.scope = scope;
	}

	protected int nextId = 0;
	protected List<BytecodeInstruction> instructions = new ArrayList<>();
	protected Map<String, Integer> labels = new HashMap<>();

	public String getNextLabel() {
		return "_" + this.nextId;
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
		if (expression instanceof Expression.NumberLiteral literal) {
			this.emit(Primitive.from(literal.value()));
			return;
		}

		if (expression instanceof Expression.StringLiteral literal) {
			this.emit(Primitive.from(literal.value()));
			return;
		}

		if (expression instanceof Expression.Identifier identifier) {
			this.emit(new BytecodeInstruction.Load(identifier.name(), identifier.position()));
			return;
		}

		if (expression instanceof Expression.Invocation invocation) {
			String method = null;

			if (invocation.target() instanceof Expression.MemberAccess memberAccess) {
				this.compile(memberAccess.receiver(), result);
				if (result.label != null) return;

				method = memberAccess.member();
			} else {
				this.compile(invocation.target(), result);
				if (result.label != null) return;
			}

			this.emit(new BytecodeInstruction.PrepareInvoke(invocation.args().size(), method, invocation.position()));
			this.compileBlock(invocation.args(), result);
			if (result.label != null) return;

			this.emit(new BytecodeInstruction.Invoke(invocation.position()));
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

	public List<BytecodeInstruction> build() {
		for (var instruction : this.instructions) {
			if (instruction instanceof BytecodeInstruction.Goto goto_1) {
				var index = this.labels.get(goto_1.label);
				if (index != null) goto_1.index = (int) index;
				continue;
			}
		}
		return this.instructions;
	}

}
