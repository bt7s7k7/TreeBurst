package bt7s7k7.treeburst.bytecode;

import static bt7s7k7.treeburst.bytecode.BytecodeInstruction.STATUS_BREAK;
import static bt7s7k7.treeburst.bytecode.BytecodeInstruction.STATUS_NORMAL;
import static bt7s7k7.treeburst.bytecode.BytecodeInstruction.STATUS_YIELD;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.runtime.ExecutionLimitReachedException;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.NativeHandle;
import bt7s7k7.treeburst.runtime.Scope;

public class ProgramFragment {
	protected Expression expression;
	protected List<BytecodeInstruction> instructions;
	protected Map<String, Integer> labels;

	public ProgramFragment(Expression expression) {
		this.expression = expression;
	}

	public ProgramFragment(List<BytecodeInstruction> instructions, Map<String, Integer> labels) {
		this.instructions = instructions;
		this.labels = labels;
	}

	public ProgramFragment(BytecodeEmitter.BuildResult build) {
		this.instructions = build.instructions();
		this.labels = build.labels();
	}

	public Expression getExpression() {
		return this.expression;
	}

	public void setExpression(Expression expression) {
		this.expression = expression;
		this.instructions = null;
		this.labels = null;
	}

	public int getLabel(String label) {
		if (this.labels == null) throw new IllegalStateException("Called getLabel on a not yet compiled ProgramFragment");
		var index = this.labels.get(label);
		if (index == null) throw new NoSuchElementException("Label '" + label + "' does not exist");
		return index.intValue();
	}

	public boolean isCompiled() {
		return this.instructions != null;
	}

	public void compile(Scope scope, ExpressionResult result) {
		if (this.isCompiled()) return;

		var emitter = new BytecodeEmitter(scope);
		emitter.compile(this.expression, result);
		if (result.label != null) return;

		var build = emitter.build();
		this.instructions = build.instructions();
		this.labels = build.labels();
	}

	public void evaluate(Scope scope, ExpressionResult result) {
		this.evaluate(0, new ValueStack(), new ArgumentStack(), scope, result);
	}

	public void evaluate(int pc, ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
		this.compile(scope, result);
		if (result.label != null) return;

		for (; pc < this.instructions.size(); pc++) {
			if (result.executionLimit != Integer.MAX_VALUE) {
				result.executionCounter++;
				if (result.executionCounter > result.executionLimit) {
					throw new ExecutionLimitReachedException("Script execution reached the limit of " + result.executionLimit + " expressions");
				}
			}

			var instruction = this.instructions.get(pc);
			if (instruction == BytecodeInstruction.Reflect.VALUE) {
				values.push(new NativeHandle(scope.globalScope.TablePrototype, this));
				continue;
			}

			var status = instruction.executeInstruction(values, arguments, scope, result);
			if (status >= 0) {
				pc = status - 1;
				continue;
			}

			if (status == STATUS_NORMAL) continue;

			if (status == STATUS_YIELD) return;

			if (status != STATUS_BREAK) throw new IllegalStateException("Status " + status + " is not valid");

			var target = this.labels.get(result.label);
			// If the label is not part of this fragment, move higher the execution stack
			if (target == null) return;

			result.label = null;
			pc = (int) target - 1;
		}

		result.value = values.pop();
		return;
	}

	@Override
	public String toString() {
		if (this.instructions == null) {
			return this.expression.toString();
		}

		return BytecodeInstruction.format(this.instructions, this.labels);
	}
}
