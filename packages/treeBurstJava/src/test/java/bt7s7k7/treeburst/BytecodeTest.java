package bt7s7k7.treeburst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import bt7s7k7.treeburst.bytecode.ProgramFragment;
import bt7s7k7.treeburst.parsing.TreeBurstParser;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.support.InputDocument;
import bt7s7k7.treeburst.support.ManagedValue;

class BytecodeTest {
	public static class CodeResult {
		public final ManagedValue value;
		public final ProgramFragment fragment;

		private CodeResult(ManagedValue value, ProgramFragment fragment) {
			this.value = value;
			this.fragment = fragment;
		}

		public static CodeResult get(String code) {
			var document = new InputDocument("anon", code);
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

			return new CodeResult(result.value, fragment);
		}
	}

	@Test
	void basicTest() {
		var result = CodeResult.get("1 + 2");
		assertEquals("[number 3.0]", result.value.toString());
	}

	@Test
	void dynamicTest() {
		var result = CodeResult.get("$x = 1\n$y = 2\nx + y");
		assertEquals("[number 3.0]", result.value.toString());
	}

	@Test
	void receiverTest() {
		var result = CodeResult.get("$x = Table.new({ a: \\(this) this }), x.a() == x");
		assertEquals("[boolean true]", result.value.toString());
	}

	@Test
	public void andTest() {
		var result = CodeResult.get("true && 3");
		assertEquals("[number 3.0]", result.value.toString());
	}

	@Test
	public void ifTest() {
		var result = CodeResult.get("false ? 'a' : true ? 5 + 7 : 4 / 2");
		assertEquals("[number 12.0]", result.value.toString());
	}

	@Test
	public void whileTest() {
		var result = CodeResult.get("""
				$list = [1, 2, 3]
				$i = 0

				@while(i < list.length, (
				    i = i + 1
				))

				i""");
		assertEquals("[number 3.0]", result.value.toString());
	}
}
