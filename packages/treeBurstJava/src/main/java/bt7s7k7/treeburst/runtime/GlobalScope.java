package bt7s7k7.treeburst.runtime;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateExpression;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.getValueName;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_RETURN;
import static bt7s7k7.treeburst.support.ManagedValueUtils.BINARY_OPERATOR_PARAMETERS;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureBoolean;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureExpression;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureString;
import static bt7s7k7.treeburst.support.ManagedValueUtils.verifyArguments;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.standard.ArrayPrototype;
import bt7s7k7.treeburst.standard.MapPrototype;
import bt7s7k7.treeburst.standard.TableApi;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.ManagedValueUtils;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class GlobalScope extends Scope {

	private static final ArrayList<Map.Entry<String, NativeFunction.Handler>> OPERATOR_FALLBACKS = new ArrayList<>();

	@FunctionalInterface
	private interface OperatorFallbackImpl {
		public void execute(String name, ManagedValue a, ManagedValue b, Scope scope, ExpressionResult result);
	}

	private static void makeOperatorFallback(String name, OperatorFallbackImpl fallback) {
		OPERATOR_FALLBACKS.add(new AbstractMap.SimpleEntry<>(name, (args, scope, result) -> {
			if (!verifyArguments(args, List.of("this", "other"), result)) return;
			var self = args.get(0);
			var a = args.get(1);
			if (args.size() > 2) {
				var b = args.get(2);
				fallback.execute(name, a, b, scope, result);
				return;
			}

			evaluateInvocation(Primitive.VOID, a, name, Position.INTRINSIC, List.of(self, a), scope, result);
		}));
	}

	private static final OperatorFallbackImpl defaultFallback = (name, a, b, scope, result) -> {
		result.setException(new Diagnostic("Operands \"" + getValueName(a) + "\" and \"" + getValueName(b) + "\" do not support operator \"" + name + "\"", Position.INTRINSIC));
	};

	private static void makeOperatorFallback(String name) {
		makeOperatorFallback(name, defaultFallback);
	}

	static {
		makeOperatorFallback(OperatorConstants.OPERATOR_EQ, (name, a, b, scope, result) -> result.value = Primitive.from(a.equals(b)));
		makeOperatorFallback(OperatorConstants.OPERATOR_NEQ, (name, a, b, scope, result) -> {
			evaluateInvocation(a, a, OperatorConstants.OPERATOR_EQ, Position.INTRINSIC, List.of(b), scope, result);
			if (result.label != null) return;
			evaluateInvocation(result.value, result.value, OperatorConstants.OPERATOR_NOT, Position.INTRINSIC, Collections.emptyList(), scope, result);
		});

		makeOperatorFallback(OperatorConstants.OPERATOR_ADD);
		makeOperatorFallback(OperatorConstants.OPERATOR_SUB);
		makeOperatorFallback(OperatorConstants.OPERATOR_MUL);
		makeOperatorFallback(OperatorConstants.OPERATOR_DIV);
		makeOperatorFallback(OperatorConstants.OPERATOR_MOD);
		makeOperatorFallback(OperatorConstants.OPERATOR_POW);
	}

	private final static ArrayList<Map.Entry<String, NativeFunction.Handler>> NUMERIC_OPERATORS = new ArrayList<>();

	@FunctionalInterface
	private interface NumberOperatorImpl {
		ManagedValue evaluate(double a, double b);
	}

	private static void makeNumberOperator(String name, NumberOperatorImpl operator) {
		NUMERIC_OPERATORS.add(new AbstractMap.SimpleEntry<>(name, (args, scope, result) -> {
			var operands = ManagedValueUtils.prepareBinaryOperator(name, Primitive.Number.class, Primitive.Number.class, args, scope, result);
			if (operands == null) return;
			var a = operands.left().getNumberValue();
			var b = operands.right().getNumberValue();
			result.value = operator.evaluate(a, b);
			return;
		}));
	}

	static {
		makeNumberOperator(OperatorConstants.OPERATOR_ADD, (a, b) -> Primitive.from(a + b));
		makeNumberOperator(OperatorConstants.OPERATOR_SUB, (a, b) -> Primitive.from(a - b));
		makeNumberOperator(OperatorConstants.OPERATOR_MUL, (a, b) -> Primitive.from(a * b));
		makeNumberOperator(OperatorConstants.OPERATOR_DIV, (a, b) -> Primitive.from(a / b));
		makeNumberOperator(OperatorConstants.OPERATOR_MOD, (a, b) -> Primitive.from(a % b));
		makeNumberOperator(OperatorConstants.OPERATOR_POW, (a, b) -> Primitive.from(Math.pow(a, b)));

		makeNumberOperator(OperatorConstants.OPERATOR_LT, (a, b) -> Primitive.from(a < b));
		makeNumberOperator(OperatorConstants.OPERATOR_GT, (a, b) -> Primitive.from(a > b));
		makeNumberOperator(OperatorConstants.OPERATOR_GTE, (a, b) -> Primitive.from(a >= b));
		makeNumberOperator(OperatorConstants.OPERATOR_LTE, (a, b) -> Primitive.from(a <= b));

		makeNumberOperator(OperatorConstants.OPERATOR_BIT_XOR, (a, b) -> Primitive.from((int) a ^ (int) b));
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_AND, (a, b) -> Primitive.from((int) a & (int) b));
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_OR, (a, b) -> Primitive.from((int) a | (int) b));
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_SHL, (a, b) -> Primitive.from((int) a << (int) b));
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_SHR, (a, b) -> Primitive.from((int) a >> (int) b));
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_SHR_UNSIGNED, (a, b) -> Primitive.from((int) a >>> (int) b));
	}

	public final ManagedTable TablePrototype = new ManagedTable(null);
	public final ManagedTable Table = this.declareGlobal("Table", new TableApi(this.TablePrototype, this));

	public final ManagedTable FunctionPrototype = new ManagedTable(this.TablePrototype);
	public final ManagedTable Function = this.declareGlobal("Function", new ManagedTable(this.TablePrototype));

	public final ManagedTable NumberPrototype = new ManagedTable(this.TablePrototype);
	public final ManagedTable Number = this.declareGlobal("Number", new ManagedTable(this.TablePrototype));

	public final ManagedTable StringPrototype = new ManagedTable(this.TablePrototype);
	public final ManagedTable String = this.declareGlobal("String", new ManagedTable(this.TablePrototype));

	public final ManagedTable BooleanPrototype = new ManagedTable(this.TablePrototype);
	public final ManagedTable Boolean = this.declareGlobal("Boolean", new ManagedTable(this.TablePrototype));

	public final ManagedTable ArrayPrototype = new ArrayPrototype(this.TablePrototype, this);
	public final ManagedTable Array = this.declareGlobal("Array", new ManagedTable(this.TablePrototype));

	public final ManagedTable MapPrototype = new MapPrototype(this.TablePrototype, this);
	public final ManagedTable Map = this.declareGlobal("Map", new ManagedTable(this.TablePrototype));

	public <T extends ManagedValue> T declareGlobal(String name, T value) {
		var variable = this.declareVariable(name);
		if (variable == null) {
			throw new IllegalArgumentException("Duplicate declaration of global \"" + name + "\"");
		}

		variable.value = value;
		if (value instanceof ManagedObject managedObject) {
			if (managedObject.name == null) {
				managedObject.name = name;
			}
		}

		return value;
	}

	public String inspect(ManagedValue value) {
		return this.inspect(value, 5);
	}

	public String inspect(ManagedValue value, int depth) {
		var result = new ExpressionResult();

		var output = tryInspect(value, depth, result);
		if (output == null) {
			var diagnostic = result.terminate();
			return diagnostic.format();
		}

		return output;
	}

	public String tryInspect(ManagedValue value, int depth, ExpressionResult result) {
		evaluateInvocation(value, value, OperatorConstants.OPERATOR_DUMP, Position.INTRINSIC, List.of(Primitive.from(depth)), this, result);
		if (result.label != null) return null;

		var output = ensureString(result.value, this, result);
		if (result.label != null) return null;

		return output.value;
	}

	public GlobalScope() {
		super();

		this.declareGlobal("true", Primitive.TRUE);
		this.declareGlobal("false", Primitive.FALSE);
		this.declareGlobal("null", Primitive.NULL);
		this.declareGlobal("void", Primitive.VOID);

		if (!this.Function.declareProperty("prototype", this.FunctionPrototype)) throw new IllegalStateException();
		if (!this.Number.declareProperty("prototype", this.NumberPrototype)) throw new IllegalStateException();
		if (!this.String.declareProperty("prototype", this.StringPrototype)) throw new IllegalStateException();
		if (!this.Boolean.declareProperty("prototype", this.BooleanPrototype)) throw new IllegalStateException();
		if (!this.Array.declareProperty("prototype", this.ArrayPrototype)) throw new IllegalStateException();
		if (!this.Map.declareProperty("prototype", this.MapPrototype)) throw new IllegalStateException();

		for (var kv : OPERATOR_FALLBACKS) {
			this.TablePrototype.declareProperty(kv.getKey(), NativeFunction.simple(globalScope, BINARY_OPERATOR_PARAMETERS, kv.getValue()));
		}

		for (var kv : NUMERIC_OPERATORS) {
			this.NumberPrototype.declareProperty(kv.getKey(), NativeFunction.simple(globalScope, BINARY_OPERATOR_PARAMETERS, kv.getValue()));
		}

		this.NumberPrototype.declareProperty(OperatorConstants.OPERATOR_NEG, NativeFunction.simple(globalScope, List.of("this"), List.of(Primitive.Number.class), (args, scope, result) -> {
			result.value = Primitive.from(-((Primitive.Number) args.get(0)).value);
		}));

		this.NumberPrototype.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(globalScope, List.of("this", "depth?"), List.of(Primitive.Number.class, Primitive.Number.class), (args, scope, result) -> {
			var self = args.get(0).getNumberValue();

			var string = Double.toString(self);
			if (string.endsWith(".0")) {
				string = string.substring(0, string.length() - 2);
			}

			result.value = Primitive.from(string);
		}));

		this.BooleanPrototype.declareProperty(OperatorConstants.OPERATOR_NOT, NativeFunction.simple(globalScope, List.of("this", "depth?"), List.of(Primitive.Boolean.class, Primitive.Number.class), (args, scope, result) -> {
			result.value = Primitive.from(!((Primitive.Boolean) args.get(0)).value);
		}));

		this.BooleanPrototype.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(globalScope, List.of("this"), List.of(Primitive.Boolean.class), (args, scope, result) -> {
			var self = args.get(0).getBooleanValue();
			result.value = Primitive.from(java.lang.Boolean.toString(self));
		}));

		this.StringPrototype.declareProperty(OperatorConstants.OPERATOR_ADD, NativeFunction.simple(globalScope, BINARY_OPERATOR_PARAMETERS, (args, scope, result) -> {
			String left, right;

			if (args.size() == 2) {
				args = ensureArgumentTypes(args, List.of("this", "other"), List.of(Primitive.String.class, Primitive.String.class), scope, result);
				if (result.label != null) return;

				left = args.get(0).getStringValue();
				right = args.get(1).getStringValue();
			} else {
				args = ensureArgumentTypes(args, List.of("this", "left", "right"), List.of(ManagedValue.class, Primitive.String.class, Primitive.String.class), scope, result);
				if (result.label != null) return;

				left = args.get(1).getStringValue();
				right = args.get(2).getStringValue();
			}

			result.value = Primitive.from(left + right);
		}));

		this.StringPrototype.declareProperty(OperatorConstants.OPERATOR_STRING, NativeFunction.simple(globalScope, List.of("this"), List.of(Primitive.String.class), (args, scope, result) -> {
			result.value = args.get(0);
		}));

		this.StringPrototype.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(globalScope, List.of("this", "depth?"), List.of(Primitive.String.class, Primitive.Number.class), (args, scope, result) -> {
			var self = args.get(0).getStringValue();
			result.value = Primitive.from("\"" + Primitive.String.escapeString(self) + "\"");
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_AND, NativeFunction.simple(globalScope, List.of("this", "other"), List.of(ManagedValue.class, Expression.class), (args, scope, result) -> {
			var predicateResult = args.get(0);
			var predicateValue = ensureBoolean(predicateResult, scope, result);
			if (result.label != null) return;

			var alternative = args.get(1).getNativeValue(Expression.class);

			if (predicateValue.value) {
				evaluateExpression(alternative, scope, result);
			} else {
				result.value = predicateResult;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_OR, NativeFunction.simple(globalScope, List.of("this", "other"), List.of(ManagedValue.class, Expression.class), (args, scope, result) -> {
			var predicateResult = args.get(0);
			var predicateValue = ensureBoolean(predicateResult, scope, result);
			if (result.label != null) return;

			var alternative = args.get(1).getNativeValue(Expression.class);

			if (!predicateValue.value) {
				evaluateExpression(alternative, scope, result);
			} else {
				result.value = predicateResult;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_COALESCE, NativeFunction.simple(globalScope, List.of("this", "other"), List.of(ManagedValue.class, Expression.class), (args, scope, result) -> {
			var predicateResult = args.get(0);
			if (result.label != null) return;

			var alternative = args.get(1).getNativeValue(Expression.class);

			if (predicateResult == Primitive.NULL || predicateResult == Primitive.VOID) {
				evaluateExpression(alternative, scope, result);
			} else {
				result.value = predicateResult;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_ELSE, NativeFunction.simple(globalScope, List.of("this", "other"), List.of(ManagedValue.class, Expression.class), (args, scope, result) -> {
			var predicateResult = args.get(0);
			if (result.label != null) return;

			var alternative = args.get(1).getNativeValue(Expression.class);

			if (predicateResult == Primitive.VOID) {
				evaluateExpression(alternative, scope, result);
			} else {
				result.value = predicateResult;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_BOOLEAN, NativeFunction.simple(globalScope, List.of("this"), (args, scope, result) -> {
			var self = args.get(0);

			if (self.equals(Primitive.FALSE) || self.equals(Primitive.ZERO) || self.equals(Primitive.EMPTY_STRING) || self == Primitive.VOID) {
				result.value = Primitive.FALSE;
			} else {
				result.value = Primitive.TRUE;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_IS, NativeFunction.simple(globalScope, List.of("this", "other"), (args, scope, result) -> {
			result.value = Primitive.from(args.get(0) == args.get(1));
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(globalScope, List.of("this", "depth?"), List.of(ManagedValue.class, Primitive.Number.class), (args, scope, result) -> {
			var self = args.get(0);
			var depth = args.size() > 1 ? args.get(1).getNumberValue() : 0;

			if (self instanceof ManagedTable table && depth > 0) {
				var dump = ManagedValueUtils.dumpCollection(
						table.getNameOrInheritedName(), false, "(", ")",
						table.properties.entrySet(), null, java.util.Map.Entry::getKey, java.util.Map.Entry::getValue, (int) depth - 1, scope, result);
				if (dump == null) return;

				result.value = Primitive.from(dump);
				return;
			}

			result.value = Primitive.from(self.toString());
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_STRING, NativeFunction.simple(globalScope, List.of("this"), (args, scope, result) -> {
			var self = args.get(0);

			if (self == Primitive.VOID) {
				result.value = Primitive.EMPTY_STRING;
				return;
			}

			evaluateInvocation(self, self, OperatorConstants.OPERATOR_DUMP, Position.INTRINSIC, List.of(Primitive.from(1)), scope, result);
		}));

		this.FunctionPrototype.declareProperty("call", NativeFunction.simple(globalScope, List.of("this", "receiver", "arguments"), List.of(ManagedFunction.class, ManagedValue.class, ManagedArray.class), (args, scope, result) -> {
			var self = args.get(0).getFunctionValue();
			var receiver = args.get(1);
			var arguments = args.get(2).getArrayValue();

			evaluateInvocation(receiver, Primitive.VOID, self, Position.INTRINSIC, arguments.elements, scope, result);
		}));

		this.declareGlobal("unreachable", NativeFunction.simple(globalScope, Collections.emptyList(), (args, scope, result) -> {
			result.setException(new Diagnostic("Reached unreachable code", Position.INTRINSIC));
			return;
		}));

		this.declareGlobal("@if", new NativeFunction(this.FunctionPrototype, Collections.emptyList(), (args, scope, result) -> {
			for (int i = 0; i < args.size(); i += 2) {
				if (args.size() - i < 2) {
					var elseValue = ensureExpression(args.get(i), result);
					if (result.label != null) return;
					evaluateExpression(elseValue, scope, result);
					return;
				}

				var predicate = ensureExpression(args.get(i), result);
				if (result.label != null) return;
				var thenValue = ensureExpression(args.get(i + 1), result);
				if (result.label != null) return;

				evaluateExpression(predicate, scope, result);
				if (result.label != null) return;

				var predicateValue = ensureBoolean(result.value, scope, result);
				if (result.label != null) return;

				if (predicateValue.value) {
					evaluateExpression(thenValue, scope, result);
					return;
				}
			}

			result.value = Primitive.VOID;
		}));

		this.declareGlobal("@while", NativeFunction.simple(globalScope, List.of("predicate", "body"), List.of(Expression.class, Expression.class), (args, scope, result) -> {
			var predicate = args.get(0).getNativeValue(Expression.class);
			var body = args.get(1).getNativeValue(Expression.class);
			if (result.label != null) return;

			while (true) {
				evaluateExpression(predicate, scope, result);
				if (result.label != null) return;

				var predicateValue = ensureBoolean(result.value, scope, result);
				if (result.label != null) return;

				if (!predicateValue.value) break;

				evaluateExpression(body, scope, result);
				if (result.label != null) return;
			}

			result.value = Primitive.VOID;
		}));

		this.declareGlobal("return", NativeFunction.simple(globalScope, List.of("value?"), (args, scope, result) -> {
			result.value = args.isEmpty() ? Primitive.VOID : args.get(0);
			result.label = LABEL_RETURN;
		}));

		this.declareGlobal("goto", NativeFunction.simple(globalScope, List.of("label"), List.of(Primitive.String.class), (args, scope, result) -> {
			result.value = Primitive.VOID;
			result.label = args.get(0).getStringValue();
		}));
	}
}
