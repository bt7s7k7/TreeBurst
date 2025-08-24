package bt7s7k7.treeburst.runtime;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateExpression;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.findProperty;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.getValueName;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_EXCEPTION;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureBoolean;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureExpression;
import static bt7s7k7.treeburst.support.ManagedValueUtils.verifyArguments;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
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
		result.value = new Diagnostic("Operands \"" + getValueName(a) + "\" and \"" + getValueName(b) + "\" do not support operator \"" + name + "\"", Position.INTRINSIC);
		result.label = LABEL_EXCEPTION;
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
			if (args.size() > 2) {
				args = ensureArgumentTypes(args, List.of("this", "left", "right"), List.of(ManagedValue.class, Primitive.Number.class, Primitive.Number.class), scope, result);
				if (result.label != null) return;

				var a = (Primitive.Number) args.get(1);
				var b = (Primitive.Number) args.get(2);
				result.value = operator.evaluate(a.value, b.value);
				return;
			} else {
				args = ensureArgumentTypes(args, List.of("this", "right"), List.of(Primitive.Number.class, Primitive.Number.class), scope, result);
				if (result.label != null) return;

				var a = (Primitive.Number) args.get(0);
				var b = (Primitive.Number) args.get(1);
				result.value = operator.evaluate(a.value, b.value);
				return;
			}
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
	public final ManagedTable Table = this.declareGlobal("Table", new ManagedTable(this.TablePrototype));

	public final ManagedTable FunctionPrototype = new ManagedTable(this.TablePrototype);
	public final ManagedTable Function = this.declareGlobal("Function", new ManagedTable(this.TablePrototype));

	public final ManagedTable NumberPrototype = new ManagedTable(this.TablePrototype);
	public final ManagedTable Number = this.declareGlobal("Number", new ManagedTable(this.TablePrototype));

	public final ManagedTable StringPrototype = new ManagedTable(this.TablePrototype);
	public final ManagedTable String = this.declareGlobal("String", new ManagedTable(this.TablePrototype));

	public final ManagedTable BooleanPrototype = new ManagedTable(this.TablePrototype);
	public final ManagedTable Boolean = this.declareGlobal("Boolean", new ManagedTable(this.TablePrototype));

	public final ManagedTable ArrayPrototype = new ManagedTable(this.TablePrototype);
	public final ManagedTable Array = this.declareGlobal("Array", new ManagedTable(this.TablePrototype));

	public final ManagedTable MapPrototype = new ManagedTable(this.TablePrototype);
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

	public GlobalScope() {
		super();

		this.declareGlobal("true", Primitive.from(true));
		this.declareGlobal("false", Primitive.from(false));
		this.declareGlobal("null", Primitive.NULL);
		this.declareGlobal("void", Primitive.VOID);

		if (!this.Table.declareProperty("prototype", this.TablePrototype)) throw new IllegalStateException();
		if (!this.Function.declareProperty("prototype", this.FunctionPrototype)) throw new IllegalStateException();
		if (!this.Number.declareProperty("prototype", this.NumberPrototype)) throw new IllegalStateException();
		if (!this.String.declareProperty("prototype", this.StringPrototype)) throw new IllegalStateException();
		if (!this.Boolean.declareProperty("prototype", this.BooleanPrototype)) throw new IllegalStateException();
		if (!this.Array.declareProperty("prototype", this.ArrayPrototype)) throw new IllegalStateException();
		if (!this.Map.declareProperty("prototype", this.MapPrototype)) throw new IllegalStateException();

		if (!this.Table.declareProperty("new", NativeFunction.simple(globalScope, List.of("this"), (args, scope, result) -> {
			if (!verifyArguments(args, List.of("this"), result)) return;
			var self = args.get(0);

			if (!findProperty(self, self, "prototype", scope, result)) {
				result.value = new Diagnostic("Cannot find a prototype on receiver", Position.INTRINSIC);
				result.label = LABEL_EXCEPTION;
				return;
			}

			var prototype = result.value;
			if (!(prototype instanceof ManagedTable prototype_1)) {
				result.value = new Diagnostic("Prototype must be a Table", Position.INTRINSIC);
				result.label = LABEL_EXCEPTION;
				return;
			}

			result.value = new ManagedTable(prototype_1);
		})));

		for (var kv : OPERATOR_FALLBACKS) {
			this.TablePrototype.declareProperty(kv.getKey(), NativeFunction.simple(globalScope, List.of("this", "a", "b?"), kv.getValue()));
		}

		for (var kv : NUMERIC_OPERATORS) {
			this.NumberPrototype.declareProperty(kv.getKey(), NativeFunction.simple(globalScope, List.of("this", "a", "b?"), kv.getValue()));
		}

		this.NumberPrototype.declareProperty(OperatorConstants.OPERATOR_NEG, NativeFunction.simple(globalScope, List.of("this"), List.of(Primitive.Number.class), (args, scope, result) -> {
			result.value = Primitive.from(-((Primitive.Number) args.get(0)).value);
		}));

		this.BooleanPrototype.declareProperty(OperatorConstants.OPERATOR_NOT, NativeFunction.simple(globalScope, List.of("this"), List.of(Primitive.Boolean.class), (args, scope, result) -> {
			result.value = Primitive.from(!((Primitive.Boolean) args.get(0)).value);
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_AND, NativeFunction.simple(globalScope, List.of("this", "other"), (args, scope, result) -> {
			var predicateResult = args.get(0);
			var predicateValue = ensureBoolean(predicateResult, scope, result);
			if (result.label != null) return;

			var alternative = ensureExpression(args.get(1), result);
			if (result.label != null) return;

			if (predicateValue.value) {
				evaluateExpression(alternative, scope, result);
			} else {
				result.value = predicateResult;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_OR, NativeFunction.simple(globalScope, List.of("this", "other"), (args, scope, result) -> {
			var predicateResult = args.get(0);
			var predicateValue = ensureBoolean(predicateResult, scope, result);
			if (result.label != null) return;

			var alternative = ensureExpression(args.get(1), result);
			if (result.label != null) return;

			if (!predicateValue.value) {
				evaluateExpression(alternative, scope, result);
			} else {
				result.value = predicateResult;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_COALESCE, NativeFunction.simple(globalScope, List.of("this", "other"), (args, scope, result) -> {
			var predicateResult = args.get(0);
			if (result.label != null) return;

			var alternative = ensureExpression(args.get(1), result);
			if (result.label != null) return;

			if (predicateResult == Primitive.NULL || predicateResult == Primitive.VOID) {
				evaluateExpression(alternative, scope, result);
			} else {
				result.value = predicateResult;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_ELSE, NativeFunction.simple(globalScope, List.of("this", "other"), (args, scope, result) -> {
			var predicateResult = args.get(0);
			if (result.label != null) return;

			var alternative = ensureExpression(args.get(1), result);
			if (result.label != null) return;

			if (predicateResult == Primitive.VOID) {
				evaluateExpression(alternative, scope, result);
			} else {
				result.value = predicateResult;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_BOOLEAN, NativeFunction.simple(globalScope, List.of("this"), (args, scope, result) -> {
			var self = args.get(0);

			if (self.equals(Primitive.FALSE) || self.equals(Primitive.from(0)) || self.equals(Primitive.from("")) || self == Primitive.VOID) {
				result.value = Primitive.FALSE;
			} else {
				result.value = Primitive.TRUE;
			}
		}));

		this.FunctionPrototype.declareProperty("call", NativeFunction.simple(globalScope, List.of("this", "receiver", "arguments"), List.of(ManagedFunction.class, ManagedValue.class, ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedFunction) args.get(0);
			var receiver = args.get(1);
			var arguments = (ManagedArray) args.get(2);

			evaluateInvocation(receiver, Primitive.VOID, self, Position.INTRINSIC, arguments.elements, scope, result);
		}));

		this.ArrayPrototype.declareProperty(OperatorConstants.OPERATOR_AT, NativeFunction.simple(globalScope, List.of("this", "index", "value?"), (args, scope, result) -> {
			if (args.size() <= 2) {
				args = ensureArgumentTypes(args, List.of("this", "index"), List.of(ManagedArray.class, Primitive.Number.class), scope, result);
				if (result.label != null) return;

				var self = (ManagedArray) args.get(0);
				var index = (int) ((Primitive.Number) args.get(1)).value;

				index = self.normalizeIndex(index, result);
				if (result.label != null) return;

				result.value = self.elements.get(index);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "index", "value"), List.of(ManagedArray.class, Primitive.Number.class, ManagedValue.class), scope, result);
				if (result.label != null) return;

				var self = (ManagedArray) args.get(0);
				var index = (int) ((Primitive.Number) args.get(1)).value;
				var value = args.get(2);

				if (value == Primitive.VOID) {
					result.value = new Diagnostic("Cannot set an array element to void", Position.INTRINSIC);
					result.label = LABEL_EXCEPTION;
					return;
				}

				index = self.normalizeIndex(index, result);
				if (result.label != null) return;

				self.elements.set(index, value);
				result.value = value;
			}
		}));

		this.ArrayPrototype.declareProperty("truncate", NativeFunction.simple(globalScope, List.of("this", "length"), List.of(ManagedArray.class, Primitive.Number.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			var length = (int) ((Primitive.Number) args.get(1)).value;

			if (length < 0) {
				result.value = new Diagnostic("Cannot set array length to be less than zero", Position.INTRINSIC);
				result.label = LABEL_EXCEPTION;
				return;
			}

			if (length < self.elements.size()) {
				self.elements.subList(length, self.elements.size()).clear();
			} else if (length > self.elements.size()) {
				self.elements.addAll(Collections.nCopies(length - self.elements.size(), Primitive.NULL));
			}

			return;
		}));

		this.ArrayPrototype.declareProperty("clone", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			result.value = new ManagedArray(self.prototype, new ArrayList<>(self.elements));
		}));

		this.ArrayPrototype.declareProperty("clear", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			self.elements.clear();
			result.value = Primitive.VOID;
		}));

		this.ArrayPrototype.declareProperty("slice", NativeFunction.simple(globalScope, List.of("this", "from", "to?"), (args, scope, result) -> {
			if (args.size() == 2) {
				args = ensureArgumentTypes(args, List.of("this", "from"), List.of(ManagedArray.class, Primitive.Number.class), scope, result);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "from", "to"), List.of(ManagedArray.class, Primitive.Number.class, Primitive.Number.class), scope, result);
			}

			if (result.label != null) return;
			var self = (ManagedArray) args.get(0);
			var from = (int) ((Primitive.Number) args.get(1)).value;
			var to = args.size() == 2 ? self.elements.size() : (int) ((Primitive.Number) args.get(2)).value;

			from = self.normalizeIndex(from, result);
			if (result.label != null) return;

			to = self.normalizeLimit(to, result);
			if (result.label != null) return;

			result.value = new ManagedArray(self.prototype, new ArrayList<>(self.elements.subList(from, to)));
		}));

		this.ArrayPrototype.declareProperty("splice", NativeFunction.simple(globalScope, List.of("this", "index", "delete", "insert?"), (args, scope, result) -> {
			if (args.size() == 3) {
				args = ensureArgumentTypes(args, List.of("this", "index", "delete"), List.of(ManagedArray.class, Primitive.Number.class, Primitive.Number.class), scope, result);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "index", "delete", "insert"), List.of(ManagedArray.class, Primitive.Number.class, Primitive.Number.class, ManagedArray.class), scope, result);
			}

			if (result.label != null) return;
			var self = (ManagedArray) args.get(0);
			var index = (int) ((Primitive.Number) args.get(1)).value;
			var delete = (int) ((Primitive.Number) args.get(2)).value;
			var insert = args.size() == 3 ? null : (ManagedArray) args.get(3);

			index = self.normalizeLimit(index, result);
			if (result.label != null) return;

			if (index + delete > self.elements.size()) {
				result.value = new Diagnostic("Too many elements to delete, deleting " + delete + " at index " + index + " in array of size " + self.elements.size(), Position.INTRINSIC);
				result.label = LABEL_EXCEPTION;
				return;
			}

			var range = self.elements.subList(index, index + delete);
			range.clear();
			if (insert != null) {
				range.addAll(insert.elements);
			}

			result.value = Primitive.VOID;
		}));

		this.ArrayPrototype.declareProperty("append", NativeFunction.simple(globalScope, List.of("this", "elements"), List.of(ManagedArray.class, ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(self.elements.size()), Primitive.from(0), args.get(1)), scope, result);
		}));

		this.ArrayPrototype.declareProperty("prepend", NativeFunction.simple(globalScope, List.of("this", "elements"), List.of(ManagedArray.class, ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(0), Primitive.from(0), args.get(1)), scope, result);
		}));

		this.ArrayPrototype.declareProperty("pop", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			var removedValue = self.elements.size() > 0 ? self.elements.getLast() : Primitive.VOID;

			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(-1), Primitive.from(1)), scope, result);

			if (result.label == null) {
				result.value = removedValue;
			}
		}));

		this.ArrayPrototype.declareProperty("shift", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			var removedValue = self.elements.size() > 0 ? self.elements.getFirst() : Primitive.VOID;

			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(0), Primitive.from(1)), scope, result);

			if (result.label == null) {
				result.value = removedValue;
			}
		}));

		this.ArrayPrototype.declareProperty("push", new NativeFunction(this.FunctionPrototype, List.of("this", "...elements"), (args, scope, result) -> {
			var args_1 = ensureArgumentTypes(args, List.of("this"), List.of(ManagedArray.class), scope, result);
			if (result.label != null) return;

			var self = (ManagedArray) args_1.get(0);
			var elementsToAdd = new ManagedArray(this.ArrayPrototype, args.subList(1, args.size()));
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(self.elements.size()), Primitive.from(0), elementsToAdd), scope, result);
		}));

		this.ArrayPrototype.declareProperty("unshift", new NativeFunction(this.FunctionPrototype, List.of("this", "...elements"), (args, scope, result) -> {
			var args_1 = ensureArgumentTypes(args, List.of("this"), List.of(ManagedArray.class), scope, result);
			if (result.label != null) return;

			var self = (ManagedArray) args_1.get(0);
			var elementsToAdd = new ManagedArray(this.ArrayPrototype, args.subList(1, args.size()));
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(0), Primitive.from(0), elementsToAdd), scope, result);
		}));

		this.MapPrototype.declareProperty(OperatorConstants.OPERATOR_AT, NativeFunction.simple(globalScope, List.of("this", "index", "value?"), (args, scope, result) -> {
			if (args.size() <= 2) {
				args = ensureArgumentTypes(args, List.of("this", "index"), List.of(ManagedMap.class, ManagedValue.class), scope, result);
				if (result.label != null) return;

				var self = (ManagedMap) args.get(0);
				var index = args.get(1);

				var content = self.entries.get(index);
				if (content == null) {
					result.value = Primitive.VOID;
				} else {
					result.value = content;
				}
			} else {
				args = ensureArgumentTypes(args, List.of("this", "index", "value"), List.of(ManagedMap.class, ManagedValue.class, ManagedValue.class), scope, result);
				if (result.label != null) return;

				var self = (ManagedMap) args.get(0);
				var index = args.get(1);
				var value = args.get(2);

				if (index == Primitive.VOID) {
					result.value = new Diagnostic("Cannot set a map entry with a void key", Position.INTRINSIC);
					result.label = LABEL_EXCEPTION;
					return;
				}

				if (value == Primitive.VOID) {
					self.entries.remove(index);
				} else {
					self.entries.put(index, value);
				}

				result.value = value;
			}
		}));

		this.MapPrototype.declareProperty("clone", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			result.value = new ManagedMap(self.prototype, new HashMap<>(self.entries));
		}));

		this.MapPrototype.declareProperty("clear", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			self.entries.clear();
			result.value = Primitive.VOID;
		}));

		this.MapPrototype.declareProperty("keys", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			result.value = new ManagedArray(this.ArrayPrototype, new ArrayList<>(self.entries.keySet()));
		}));

		this.MapPrototype.declareProperty("values", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			result.value = new ManagedArray(this.ArrayPrototype, new ArrayList<>(self.entries.values()));
		}));

		this.MapPrototype.declareProperty("entries", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			var entries = new ManagedArray(this.ArrayPrototype, new ArrayList<>(self.entries.size()));

			for (var kv : self.entries.entrySet()) {
				entries.elements.add(new ManagedArray(this.ArrayPrototype, List.of(kv.getKey(), kv.getValue())));
			}

			result.value = entries;
		}));

		this.declareGlobal("unreachable", NativeFunction.simple(globalScope, Collections.emptyList(), (args, scope, result) -> {
			result.value = new Diagnostic("Reached unreachable code", Position.INTRINSIC);
			result.label = LABEL_EXCEPTION;
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

		if (!this.Table.declareProperty(OperatorConstants.OPERATOR_IS, NativeFunction.simple(globalScope, List.of("this", "other"), (args, scope, result) -> {
			if (!verifyArguments(args, List.of("this", "other"), result)) return;
			result.value = Primitive.from(args.get(0) == args.get(1));
		})));
	}
}
