package bt7s7k7.treeburst;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateExpression;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_RETURN;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureBoolean;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.opentest4j.AssertionFailedError;

import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.parsing.TreeBurstParser;
import bt7s7k7.treeburst.runtime.ExpressionEvaluator;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.runtime.NativeHandle;
import bt7s7k7.treeburst.standard.NativeHandleWrapper;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.InputDocument;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

class AutomaticTest {
	private static class DummyObject {
		public double a;
		public double b;

		public final HashMap<String, Double> numbers = new HashMap<>();

		public static final NativeHandleWrapper<DummyObject> WRAPPER = new NativeHandleWrapper<>("DummyObject", DummyObject.class, ctx -> ctx
				.addProperty("a", Primitive.Number.class, v -> Primitive.from(v.a), (v, a) -> v.a = a.value)
				.addProperty("b", Primitive.Number.class, v -> Primitive.from(v.b), (v, b) -> v.b = b.value)
				.addGetter("sum", v -> Primitive.from(v.a + v.b))
				.addMapAccess(v -> v.numbers, Primitive.String.class, Primitive.Number.class, Primitive::from, ManagedValue::getStringValue, Primitive::from, ManagedValue::getNumberValue));
	}

	private static class Test {
		public final String name;
		public boolean expectFail = false;
		public List<Diagnostic> errors = new ArrayList<>();
		public final InputDocument document;

		public Test(String name, InputDocument document) {
			this.name = name;
			this.document = document;
		}

		public boolean isFailed() {
			return (this.errors.size() > 0) != this.expectFail;
		}

		public void evaluate() {
			var parser = new TreeBurstParser(this.document);
			var root = parser.parse();

			this.errors = parser.diagnostics;

			if (!this.errors.isEmpty()) {
				return;
			}

			var globalScope = new GlobalScope();

			var counter = new Object() {
				public int count = 0;
			};

			globalScope.declareGlobal("assert", NativeFunction.simple(globalScope, List.of("predicate"), (args, scope, result) -> {
				var predicate = ensureBoolean(args.get(0), scope, result);
				if (result.label != null) return;

				if (predicate.equals(Primitive.FALSE)) {
					result.setException(new Diagnostic("Assertion failed", Position.INTRINSIC));
					return;
				}

				result.value = Primitive.VOID;
			}));

			globalScope.declareGlobal("assertEqual", NativeFunction.simple(globalScope, List.of("value", "pattern"), (args, scope, result) -> {
				var value = args.get(0);
				var pattern = args.get(1);

				ExpressionEvaluator.evaluateInvocation(value, value, OperatorConstants.OPERATOR_EQ, Position.INTRINSIC, List.of(pattern), scope, result);
				if (result.label != null) return;

				var predicate = ensureBoolean(result.value, scope, result);
				if (result.label != null) return;

				if (predicate.equals(Primitive.FALSE)) {
					result.setException(new Diagnostic("Assertion failed, " + scope.globalScope.inspect(value) + " != " + scope.globalScope.inspect(pattern), Position.INTRINSIC));
					return;
				}

				result.value = Primitive.VOID;
			}));

			globalScope.declareGlobal("increment", NativeFunction.simple(globalScope, Collections.emptyList(), (args, scope, result) -> {
				counter.count++;
				result.value = Primitive.VOID;
			}));

			globalScope.declareGlobal("getCounter", NativeFunction.simple(globalScope, Collections.emptyList(), (args, scope, result) -> {
				result.value = Primitive.from(counter.count);
			}));

			globalScope.declareGlobal("dummy", new NativeHandle(DummyObject.WRAPPER.buildPrototype(globalScope), new DummyObject()));

			{
				var result = new ExpressionResult();
				evaluateExpression(root, globalScope, result);

				if (result.label == LABEL_RETURN) {
					result.label = null;
				}

				var diagnostic = result.terminate();
				if (diagnostic != null) {
					this.errors.add(diagnostic);
				}
			}
		}

		public static List<Test> parseTests(Path path, String testFile) {
			var rootDocument = new InputDocument(path.toString(), testFile);
			var tests = new ArrayList<Test>();
			var pattern = Pattern.compile("// Test: ([^,\\n]+)(?:, ([^\\n]+))?");
			var matcher = pattern.matcher(testFile);

			if (!matcher.find()) return tests;

			Test onlyTest = null;

			var isFinal = false;
			while (!isFinal) {
				var textStart = matcher.end() + 1;
				var name = matcher.group(1);
				var modifier = matcher.group(2);

				isFinal = !matcher.find();
				var textEnd = isFinal ? testFile.length() : matcher.start();

				var startLine = rootDocument.getCursorAtIndex(textStart).line;
				var test = new Test(name, new InputDocument(path.toString(), testFile.substring(textStart, textEnd), startLine));

				if (modifier != null) {
					if (modifier.contains("expect fail")) {
						test.expectFail = true;
					}

					if (modifier.contains("solo")) {
						onlyTest = test;
					}

				}

				tests.add(test);
			}

			if (onlyTest != null) return List.of(onlyTest);
			return tests;
		}
	}

	@TestFactory
	public Stream<DynamicNode> runCommonTests() {
		try {
			var testFilePath = Path.of("../../test/standard.tb").toAbsolutePath().normalize();
			var testFile = Files.readString(testFilePath);
			var tests = Test.parseTests(testFilePath, testFile);

			return tests.stream().map(test -> DynamicTest.dynamicTest(test.name, () -> {
				test.evaluate();
				if (test.isFailed()) {
					fail(String.join("\n", test.errors.stream().map(Diagnostic::format).toList()));
				}
			}));
		} catch (IOException exception) {
			throw new AssertionFailedError("Failed to load test file", exception);
		}
	}
}
