package bt7s7k7.treeburst.bytecode;

import java.util.Collections;
import java.util.List;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.support.Position;

public record RawInstructions(Position position, List<BytecodeInstruction> instructions) implements Expression {
	public static RawInstructions empty(Position position) {
		return new RawInstructions(position, Collections.emptyList());
	}
}
