package bt7s7k7.treeburst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import bt7s7k7.treeburst.bytecode.ProgramFragment;
import bt7s7k7.treeburst.parsing.TreeBurstParser;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.support.InputDocument;

class BytecodeTest {
	@Test
	void basicTest() {
		var document = new InputDocument("anon", "1 + 2");
		var parser = new TreeBurstParser(document);
		var root = parser.parse();
		assertEquals(0, parser.diagnostics.size());

		var globalScope = new GlobalScope();

		var fragment = new ProgramFragment(root);
		var result = new ExpressionResult();
		fragment.evaluate(globalScope, result);

		var error = result.terminate();
		if (error != null) {
			fail(error.format());
		}

		assertEquals("[number 3.0]", result.value.toString());
	}

	@Test
	void dynamicTest() {
		var document = new InputDocument("anon", "$x = 1\n$y = 2\nx + y");
		var parser = new TreeBurstParser(document);
		var root = parser.parse();
		assertEquals(0, parser.diagnostics.size());

		var globalScope = new GlobalScope();

		var fragment = new ProgramFragment(root);
		var result = new ExpressionResult();
		fragment.evaluate(globalScope, result);

		var error = result.terminate();
		if (error != null) {
			fail(error.format());
		}

		assertEquals("[number 3.0]", result.value.toString());
	}
}
