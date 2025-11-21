package bt7s7k7.treeburst.bytecode;

import static bt7s7k7.treeburst.bytecode.BytecodeInstruction.STATUS_BREAK;

import java.util.List;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.runtime.ExecutionLimitReachedException;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.Scope;

public class ProgramFragment {
	protected Expression expression;
	protected List<BytecodeInstruction> instructions;

	public ProgramFragment(Expression expression) {
		this.expression = expression;
	}

	public Expression getExpression() {
		return this.expression;
	}

	public void setExpression(Expression expression) {
		this.expression = expression;
		this.instructions = null;
	}

	public boolean isCompiled() {
		return this.instructions != null;
	}

	public void compile(Scope scope, ExpressionResult result) {
		if (this.isCompiled()) return;

		var emitter = new BytecodeEmitter(scope);
		emitter.compile(this.expression, result);
		if (result.label != null) return;

		this.instructions = emitter.build();
	}

	public void evaluate(Scope scope, ExpressionResult result) {
		this.compile(scope, result);

		var values = new ValueStack();
		var arguments = new ArgumentStack();

		for (int pc = 0; pc < this.instructions.size(); pc++) {
			if (result.executionLimit != Integer.MAX_VALUE) {
				result.executionCounter++;
				if (result.executionCounter > result.executionLimit) {
					throw new ExecutionLimitReachedException("Script execution reached the limit of " + result.executionLimit + " expressions");
				}
			}

			var instruction = this.instructions.get(pc);
			var status = instruction.executeInstruction(values, arguments, scope, result);
			if (status >= 0) {
				pc = status;
				continue;
			}

			if (status == STATUS_BREAK) {
				return;
			}
		}

		result.value = values.pop();
		return;
	}

	@Override
	public String toString() {
		if (this.instructions == null) {
			return this.expression.toString();
		}

		return BytecodeInstruction.format(this.instructions);
	}
}
