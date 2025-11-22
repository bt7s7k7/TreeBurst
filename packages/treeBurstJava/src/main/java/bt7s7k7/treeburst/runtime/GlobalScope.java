package bt7s7k7.treeburst.runtime;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.getValueName;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_RETURN;
import static bt7s7k7.treeburst.support.ManagedValueUtils.BINARY_OPERATOR_PARAMETERS;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureExpression;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureString;
import static bt7s7k7.treeburst.support.ManagedValueUtils.prepareBinaryOperator;
import static bt7s7k7.treeburst.support.ManagedValueUtils.verifyArguments;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

import bt7s7k7.treeburst.bytecode.ArgumentStack;
import bt7s7k7.treeburst.bytecode.BytecodeEmitter;
import bt7s7k7.treeburst.bytecode.BytecodeInstruction;
import bt7s7k7.treeburst.bytecode.ValueStack;
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
		NUMERIC_OPERATORS.add(new AbstractMap.SimpleEntry<>(name, (args, scope, result) -> { // @symbol: <template>numberOperator, @kind: function
			var operands = prepareBinaryOperator(name, Primitive.Number.class, Primitive.Number.class, args, scope, result);
			if (operands == null) return;
			var a = operands.left().getNumberValue();
			var b = operands.right().getNumberValue();
			result.value = operator.evaluate(a, b);
			return;
		}));
	}

	static {
		makeNumberOperator(OperatorConstants.OPERATOR_ADD, (a, b) -> Primitive.from(a + b)); // @summary: Adds two numbers together.
		makeNumberOperator(OperatorConstants.OPERATOR_SUB, (a, b) -> Primitive.from(a - b)); // @summary: Subtracts the second number from the first one.
		makeNumberOperator(OperatorConstants.OPERATOR_MUL, (a, b) -> Primitive.from(a * b)); // @summary: Multiplies two numbers together.
		makeNumberOperator(OperatorConstants.OPERATOR_DIV, (a, b) -> Primitive.from(a / b)); // @summary: Divides the first number by the second one.
		makeNumberOperator(OperatorConstants.OPERATOR_MOD, (a, b) -> Primitive.from(a % b)); // @summary: Returns the remainder of the first number when divided by the second.
		makeNumberOperator(OperatorConstants.OPERATOR_POW, (a, b) -> Primitive.from(Math.pow(a, b))); // @summary: Puts the first number to the second's power.

		makeNumberOperator(OperatorConstants.OPERATOR_LT, (a, b) -> Primitive.from(a < b)); // @summary: Returns `true` if the first number is less than the second.
		makeNumberOperator(OperatorConstants.OPERATOR_GT, (a, b) -> Primitive.from(a > b)); // @summary: Returns `true` if the first number is greater than the second.
		makeNumberOperator(OperatorConstants.OPERATOR_GTE, (a, b) -> Primitive.from(a >= b)); // @summary: Returns `true` if the first number is greater or equal to the second.
		makeNumberOperator(OperatorConstants.OPERATOR_LTE, (a, b) -> Primitive.from(a <= b)); // @summary: Returns `true` if the first number is less or equal to the second.

		makeNumberOperator(OperatorConstants.OPERATOR_BIT_XOR, (a, b) -> Primitive.from((int) a ^ (int) b)); // @summary: Performs a bitwise XOR over the two numbers, calculated using 32-bit signed integers.
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_AND, (a, b) -> Primitive.from((int) a & (int) b)); // @summary: Performs a bitwise AND over the two numbers, calculated using 32-bit signed integers.
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_OR, (a, b) -> Primitive.from((int) a | (int) b)); // @summary: Performs a bitwise OR over the two numbers, calculated using 32-bit signed integers.
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_SHL, (a, b) -> Primitive.from((int) a << (int) b)); // @summary: Shifts the bits in the first number left by the value of the second number, calculated using 32-bit signed integers.
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_SHR, (a, b) -> Primitive.from((int) a >> (int) b)); // @summary: Shifts the bits in the first number right by the value of the second number, calculated using 32-bit signed integers.
		makeNumberOperator(OperatorConstants.OPERATOR_BIT_SHR_UNSIGNED, (a, b) -> Primitive.from((int) a >>> (int) b)); // @summary: Shifts the bits in the first number right by the value of the second number, calculated using 32-bit unsigned integers.
	}

	private class RangeList extends AbstractList<ManagedValue> implements RandomAccess {
		public final int min;
		public final int max;

		public RangeList(int min, int max) {
			this.min = min;
			this.max = max;
		}

		public RangeList(int max) {
			this.min = 0;
			this.max = max;
		}

		@Override
		public int size() {
			return this.max - this.min;
		}

		@Override
		public ManagedValue get(int index) {
			return Primitive.from(this.min + index);
		}
	}

	public final ManagedTable TablePrototype = new ManagedTable(null);
	public final ManagedTable Table = this.declareGlobal("Table", new TableApi(this.TablePrototype, this));

	public final ManagedTable FunctionPrototype = new ManagedTable(this.TablePrototype); /// @symbol:Function.prototype
	public final ManagedTable Function = this.declareGlobal("Function", new ManagedTable(this.TablePrototype));

	public final ManagedTable NumberPrototype = new ManagedTable(this.TablePrototype); /// @symbol:Number.prototype
	public final ManagedTable Number = this.declareGlobal("Number", new ManagedTable(this.TablePrototype)); // @summary: Represents a real number.

	public final ManagedTable StringPrototype = new ManagedTable(this.TablePrototype); /// @symbol:String.prototype
	public final ManagedTable String = this.declareGlobal("String", new ManagedTable(this.TablePrototype)); // @summary: Represents a string of characters.

	public final ManagedTable BooleanPrototype = new ManagedTable(this.TablePrototype); /// @symbol:Boolean.prototype
	public final ManagedTable Boolean = this.declareGlobal("Boolean", new ManagedTable(this.TablePrototype)); // @summary: Represents a truth value of either `true` or `false`.

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

		var output = this.tryInspect(value, depth, result);
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

		this.declareGlobal("true", Primitive.TRUE); // @type: Boolean, @summary: Constant value of `true`
		this.declareGlobal("false", Primitive.FALSE); // @type: Boolean, @summary: Constant value of `false`
		this.declareGlobal("null", Primitive.NULL); // @summary: Object representing an empty value
		this.declareGlobal("void", Primitive.VOID); // @summary: Object representing a missing or non-existent value.

		if (!this.Function.declareProperty("prototype", this.FunctionPrototype)) throw new IllegalStateException();
		if (!this.Number.declareProperty("prototype", this.NumberPrototype)) throw new IllegalStateException();
		if (!this.String.declareProperty("prototype", this.StringPrototype)) throw new IllegalStateException();
		if (!this.Boolean.declareProperty("prototype", this.BooleanPrototype)) throw new IllegalStateException();
		if (!this.Array.declareProperty("prototype", this.ArrayPrototype)) throw new IllegalStateException();
		if (!this.Map.declareProperty("prototype", this.MapPrototype)) throw new IllegalStateException();

		for (var kv : OPERATOR_FALLBACKS) {
			this.TablePrototype.declareProperty(kv.getKey(), NativeFunction.simple(this.globalScope, BINARY_OPERATOR_PARAMETERS, kv.getValue()));
		}

		for (var kv : NUMERIC_OPERATORS) {
			this.NumberPrototype.declareProperty(kv.getKey(), NativeFunction.simple(this.globalScope, BINARY_OPERATOR_PARAMETERS, kv.getValue()));
		}

		this.NumberPrototype.declareProperty(OperatorConstants.OPERATOR_NEG, NativeFunction.simple(this.globalScope, List.of("this"), List.of(Primitive.Number.class), (args, scope, result) -> {
			// @summary: Returns a number with an inverted sign.
			result.value = Primitive.from(-((Primitive.Number) args.get(0)).value);
		}));

		this.NumberPrototype.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(this.globalScope, List.of("this", "depth?"), List.of(Primitive.Number.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary: Formats the number into a textual form.
			var self = args.get(0).getNumberValue();

			var string = Double.toString(self);
			if (string.endsWith(".0")) {
				string = string.substring(0, string.length() - 2);
			}

			result.value = Primitive.from(string);
		}));

		this.NumberPrototype.declareProperty(OperatorConstants.OPERATOR_STRING, NativeFunction.simple(this.globalScope, List.of("this", "radix?"), List.of(Primitive.Number.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary[[Formats the number into a textual form. If the `radix` parameter is
			// provided, it is used as the base for representing the number value. In this case the
			// number is converted to an integer by rounding down.]]
			var self = args.get(0).getNumberValue();

			if (args.size() != 1) {
				var radix = (int) args.get(1).getNumberValue();

				if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
					result.setException(new Diagnostic("Radix argument " + radix + " is not between " + Character.MIN_RADIX + " and " + Character.MAX_RADIX, Position.INTRINSIC));
					return;
				}

				var value = (long) Math.floor(self);
				result.value = Primitive.from(Long.toString(value, radix));
				return;
			}

			var string = Double.toString(self);
			if (string.endsWith(".0")) {
				string = string.substring(0, string.length() - 2);
			}

			result.value = Primitive.from(string);
		}));

		this.BooleanPrototype.declareProperty(OperatorConstants.OPERATOR_NOT, NativeFunction.simple(this.globalScope, List.of("this", "depth?"), List.of(Primitive.Boolean.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary: Returns an inverted value of the boolean.
			result.value = Primitive.from(!((Primitive.Boolean) args.get(0)).value);
		}));

		this.BooleanPrototype.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(this.globalScope, List.of("this"), List.of(Primitive.Boolean.class), (args, scope, result) -> {
			// @summary: Formats the boolean into a textual form.
			var self = args.get(0).getBooleanValue();
			result.value = Primitive.from(java.lang.Boolean.toString(self));
		}));

		this.StringPrototype.declareProperty(OperatorConstants.OPERATOR_ADD, NativeFunction.simple(this.globalScope, BINARY_OPERATOR_PARAMETERS, (args, scope, result) -> {
			// @summary: Concatenates two strings together.
			var operands = prepareBinaryOperator(OperatorConstants.OPERATOR_ADD, Primitive.String.class, Primitive.String.class, args, scope, result);
			if (result.label != null) return;

			var left = operands.left().getStringValue();
			var right = operands.right().getStringValue();

			result.value = Primitive.from(left + right);
		}));

		this.StringPrototype.declareProperty(OperatorConstants.OPERATOR_STRING, NativeFunction.simple(this.globalScope, List.of("this"), List.of(Primitive.String.class), (args, scope, result) -> {
			// @summary: Returns the string unchanged.
			result.value = args.get(0);
		}));

		this.StringPrototype.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(this.globalScope, List.of("this", "depth?"), List.of(Primitive.String.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary: Formats the string into a textual form, which is surrounded by `"` characters and all special characters are escaped.
			var self = args.get(0).getStringValue();
			result.value = Primitive.from("\"" + Primitive.String.escapeString(self) + "\"");
		}));

		this.StringPrototype.declareProperty(OperatorConstants.OPERATOR_MUL, NativeFunction.simple(this.globalScope, BINARY_OPERATOR_PARAMETERS, (args, scope, result) -> {
			// @summary[[Creates a new string that is the input string repeated `n` times.]]
			var operands = prepareBinaryOperator(OperatorConstants.OPERATOR_ADD, Primitive.String.class, Primitive.Number.class, args, scope, result);
			if (result.label != null) return;

			var left = operands.left().getStringValue();
			var right = (int) operands.right().getNumberValue();
			var output = new StringBuilder(left.length() * right);

			for (int i = 0; i < right; i++) {
				output.append(left);
			}

			result.value = Primitive.from(output.toString());
		}));

		this.StringPrototype.declareProperty("pad", NativeFunction.simple(this.globalScope, List.of("this", "length", "fill?"), List.of(Primitive.String.class, Primitive.Number.class, Primitive.String.class), (args, scope, result) -> {
			// @summary[[If the string is shorter that `length`, creates a new string that is padded
			// with the `fill` character (or space character if not provided) to reach the desired
			// length. The string is padded to be right aligned, use {@link String.prototype.padLeft} for left alignment.]]

			var self = args.get(0).getStringValue();
			var length = (int) args.get(1).getNumberValue();
			var fill = args.size() == 2 ? " " : args.get(2).getStringValue();

			if (self.length() >= length) {
				result.value = args.get(0);
				return;
			}

			var builder = new StringBuilder(length);
			var fillCount = length - self.length();

			for (int i = 0; i < fillCount; i++) {
				builder.append(fill);
			}

			builder.append(self);

			result.value = Primitive.from(builder.toString());
		}));

		this.StringPrototype.declareProperty("padLeft", NativeFunction.simple(this.globalScope, List.of("this", "length", "fill?"), List.of(Primitive.String.class, Primitive.Number.class, Primitive.String.class), (args, scope, result) -> {
			// @summary[[If the string is shorter that `length`, creates a new string that is padded
			// with the `fill` character (or space character if not provided) to reach the desired
			// length. The resulting string is left aligned.]]

			var self = args.get(0).getStringValue();
			var length = (int) args.get(1).getNumberValue();
			var fill = args.size() == 2 ? " " : args.get(2).getStringValue();

			if (self.length() >= length) {
				result.value = args.get(0);
				return;
			}

			var builder = new StringBuilder(length);
			builder.append(self);

			var fillCount = length - self.length();

			for (int i = 0; i < fillCount; i++) {
				builder.append(fill);
			}

			result.value = Primitive.from(builder.toString());
		}));

		this.String.declareProperty("fromCharCode", NativeFunction.simple(this.globalScope, List.of("code"), List.of(Primitive.Number.class), (args, scope, result) -> {
			// @summary: Returns a string containing a character with the provided character code. The codepage is implementation dependent, but it's probably UTF-16.
			var code = (int) args.get(0).getNumberValue();
			var charString = "" + (char) code;
			result.value = Primitive.from(charString);
		}));

		this.StringPrototype.declareProperty("getCharCode", NativeFunction.simple(this.globalScope, List.of("this", "index?"), List.of(Primitive.String.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary[[Returns the code for a character in the string. If `index` is not provided,
			// returns the code for the first character. As always, the index may be negative to
			// index from the end of the string, where `-1` is the last character and so on.]]
			var self = (Primitive.String) args.get(0);
			var index = args.size() == 1 ? 0 : (int) args.get(1).getNumberValue();

			index = self.normalizeIndex(index, result);
			if (result.label != null) return;

			var code = self.value.charAt(index);
			result.value = Primitive.from(code);
		}));

		this.StringPrototype.declareProperty(OperatorConstants.OPERATOR_AT, NativeFunction.simple(this.globalScope, List.of("this", "index?"), List.of(Primitive.String.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary[[Returns the a character from the string at an `index`. The return value is
			// a string of length `1`, containing the selected character. As always, the index may
			// be negative to index from the end of the string, where `-1` is the last character and
			// so on.]]
			var self = (Primitive.String) args.get(0);
			var index = args.size() == 1 ? 0 : (int) args.get(1).getNumberValue();

			index = self.normalizeIndex(index, result);
			if (result.label != null) return;

			var code = self.value.charAt(index);
			result.value = Primitive.from("" + code);
		}));

		this.StringPrototype.declareProperty("slice", NativeFunction.simple(this.globalScope, List.of("this", "from", "to?"), (args, scope, result) -> {
			// @summary[[Gets a section of the string starting at `from` and ending at `to` (or the
			// end of the string if not provided). As always, the index may be negative to index
			// from the end of the string, where `-1` is the last character and so on.]]
			if (args.size() == 2) {
				args = ensureArgumentTypes(args, List.of("this", "from"), List.of(Primitive.String.class, Primitive.Number.class), scope, result);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "from", "to"), List.of(Primitive.String.class, Primitive.Number.class, Primitive.Number.class), scope, result);
			}

			if (result.label != null) return;
			var self = (Primitive.String) args.get(0);
			var from = (int) args.get(1).getNumberValue();
			var to = args.size() == 2 ? self.value.length() : (int) args.get(2).getNumberValue();

			from = self.normalizeIndex(from, result);
			if (result.label != null) return;

			to = self.normalizeLimit(to, result);
			if (result.label != null) return;

			result.value = Primitive.from(self.value.substring(from, to));
		}));

		this.StringPrototype.declareProperty("startsWith", NativeFunction.simple(this.globalScope, List.of("this", "substring", "index?"), (args, scope, result) -> {
			// @summary[[Tests if the string starts with the substring. If `index` is provided, the
			// substring is expected at this position. As always, the index may be negative to index
			// from the end of the string, where `-1` is the last character and so on.]]
			if (args.size() == 2) {
				args = ensureArgumentTypes(args, List.of("this", "substring"), List.of(Primitive.String.class, Primitive.String.class), scope, result);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "substring", "to"), List.of(Primitive.String.class, Primitive.String.class, Primitive.Number.class), scope, result);
			}

			if (result.label != null) return;
			var self = (Primitive.String) args.get(0);
			var substring = args.get(1).getStringValue();
			var index = args.size() == 2 ? 0 : (int) args.get(2).getNumberValue();

			index = self.normalizeIndex(index, result);
			if (result.label != null) return;

			result.value = Primitive.from(self.value.startsWith(substring, index));
		}));

		this.StringPrototype.declareProperty("endsWith", NativeFunction.simple(this.globalScope, List.of("this", "substring"), List.of(Primitive.String.class, Primitive.String.class), (args, scope, result) -> {
			// @summary[[Tests if the string ends with the substring.]]
			var self = (Primitive.String) args.get(0);
			var substring = args.get(1).getStringValue();

			result.value = Primitive.from(self.value.endsWith(substring));
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_AND, NativeFunction.simple(this.globalScope, List.of("this", "other", "@"), List.of(Expression.class, Expression.class, BytecodeEmitter.class), (args, scope, result) -> {
			// @summary: This object is converted to a {@link Boolean}. If the result is `true`, the `other` expression is evaluated and the result retuned, otherwise this object is returned.
			var a = args.get(0).getNativeValue(Expression.class);
			var b = args.get(1).getNativeValue(Expression.class);

			var emitter = args.get(2).getNativeValue(BytecodeEmitter.class);
			var position = emitter.nextPosition;

			emitter.compile(a, result);
			if (result.label != null) return;

			var label = emitter.getNextLabel() + "_false";

			emitter.emit(BytecodeInstruction.Duplicate.VALUE);
			emitter.emit(new BytecodeInstruction.Conditional(label, false, position));
			emitter.emit(BytecodeInstruction.Discard.VALUE);
			emitter.compile(b, result);
			if (result.label != null) return;
			emitter.label(label);

			result.value = Primitive.VOID;
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_OR, NativeFunction.simple(this.globalScope, List.of("this", "other", "@"), List.of(Expression.class, Expression.class, BytecodeEmitter.class), (args, scope, result) -> {
			// @summary: This object is converted to a {@link Boolean}. If the result is `true` this object is retuned, otherwise the `other` expression is evaluated and the result retuned.
			var a = args.get(0).getNativeValue(Expression.class);
			var b = args.get(1).getNativeValue(Expression.class);

			var emitter = args.get(2).getNativeValue(BytecodeEmitter.class);
			var position = emitter.nextPosition;

			emitter.compile(a, result);
			if (result.label != null) return;

			var label = emitter.getNextLabel() + "_true";

			emitter.emit(BytecodeInstruction.Duplicate.VALUE);
			emitter.emit(new BytecodeInstruction.Conditional(label, true, position));
			emitter.emit(BytecodeInstruction.Discard.VALUE);
			emitter.compile(b, result);
			if (result.label != null) return;
			emitter.label(label);

			result.value = Primitive.VOID;
		}));

		var COALESCE_INSTRUCTION = new BytecodeInstruction() {
			@Override
			public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
				var value = values.peek();
				values.push(value == Primitive.NULL || value == Primitive.VOID ? Primitive.TRUE : Primitive.FALSE);
				return STATUS_NORMAL;
			}

			@Override
			public java.lang.String toString() {
				return "Coalesce";
			}
		};

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_COALESCE, NativeFunction.simple(this.globalScope, List.of("this", "other", "@"), List.of(Expression.class, Expression.class, BytecodeEmitter.class), (args, scope, result) -> {
			// @summary: If this object is not {@link null} or {@link void}, it is returned, otherwise the `other` expression is evaluated and the result retuned.
			var a = args.get(0).getNativeValue(Expression.class);
			var b = args.get(1).getNativeValue(Expression.class);

			var emitter = args.get(2).getNativeValue(BytecodeEmitter.class);
			var position = emitter.nextPosition;

			emitter.compile(a, result);
			if (result.label != null) return;

			var label = emitter.getNextLabel() + "_not_null_or_void";

			emitter.emit(COALESCE_INSTRUCTION);
			emitter.emit(new BytecodeInstruction.Conditional(label, false, position));
			emitter.emit(BytecodeInstruction.Discard.VALUE);
			emitter.compile(b, result);
			if (result.label != null) return;
			emitter.label(label);

			result.value = Primitive.VOID;
		}));

		var ELSE_INSTRUCTION = new BytecodeInstruction() {
			@Override
			public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
				var value = values.peek();
				values.push(value == Primitive.VOID ? Primitive.TRUE : Primitive.FALSE);
				return STATUS_NORMAL;
			}

			@Override
			public java.lang.String toString() {
				return "Else";
			}
		};

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_ELSE, NativeFunction.simple(this.globalScope, List.of("this", "other", "@"), List.of(Expression.class, Expression.class, BytecodeEmitter.class), (args, scope, result) -> {
			// @summary: If this object is not {@link void}, it is returned, otherwise the `other` expression is evaluated and the result retuned.
			var a = args.get(0).getNativeValue(Expression.class);
			var b = args.get(1).getNativeValue(Expression.class);

			var emitter = args.get(2).getNativeValue(BytecodeEmitter.class);
			var position = emitter.nextPosition;

			emitter.compile(a, result);
			if (result.label != null) return;

			var label = emitter.getNextLabel() + "_not_void";

			emitter.emit(ELSE_INSTRUCTION);
			emitter.emit(new BytecodeInstruction.Conditional(label, false, position));
			emitter.emit(BytecodeInstruction.Discard.VALUE);
			emitter.compile(b, result);
			if (result.label != null) return;
			emitter.label(label);

			result.value = Primitive.VOID;
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_BOOLEAN, NativeFunction.simple(this.globalScope, List.of("this"), (args, scope, result) -> {
			// @summary: This object is converted to a {@link Boolean}.
			var self = args.get(0);

			if (self.equals(Primitive.FALSE) || self.equals(Primitive.ZERO) || self.equals(Primitive.EMPTY_STRING) || self == Primitive.VOID) {
				result.value = Primitive.FALSE;
			} else {
				result.value = Primitive.TRUE;
			}
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_IS, NativeFunction.simple(this.globalScope, List.of("this", "other"), (args, scope, result) -> {
			// @summary[[Returns `true` if this object is equal by reference to the other object.
			// This function returns inconsistent results for objects of type {@link String} and
			// {@link Number}, and should not be used with them. The intended use is for reference
			// comparisons between compound objects and {@link null} or {@link void}, without using
			// their overload of the `k_eq` operator.]]
			result.value = Primitive.from(args.get(0) == args.get(1));
		}));

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(this.globalScope, List.of("this", "depth?"), List.of(ManagedValue.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary: Formats the value into a textual form.
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

		this.TablePrototype.declareProperty(OperatorConstants.OPERATOR_STRING, NativeFunction.simple(this.globalScope, List.of("this"), (args, scope, result) -> {
			// @summary: Formats the value into a textual form, using its `k_dump` implementation
			// with `depth` of `1`. An exception is an {@link void} object, in which case an empty
			// string is returned.
			var self = args.get(0);

			if (self == Primitive.VOID) {
				result.value = Primitive.EMPTY_STRING;
				return;
			}

			evaluateInvocation(self, self, OperatorConstants.OPERATOR_DUMP, Position.INTRINSIC, List.of(Primitive.from(1)), scope, result);
		}));

		this.FunctionPrototype.declareProperty("call", NativeFunction.simple(this.globalScope, List.of("this", "receiver", "arguments?"), List.of(ManagedFunction.class, ManagedValue.class, ManagedArray.class), (args, scope, result) -> {
			// @summary: Calls the function with the specified receiver and arguments, returning its return value.
			var self = args.get(0).getFunctionValue();
			var receiver = args.get(1);
			var arguments = args.size() == 2 ? Collections.<ManagedValue>emptyList() : args.get(2).getArrayValue().getElementsReadOnly();

			evaluateInvocation(receiver, Primitive.VOID, self, Position.INTRINSIC, arguments, scope, result);
		}));

		this.declareGlobal("unreachable", NativeFunction.simple(this.globalScope, Collections.emptyList(), (args, scope, result) -> {
			// @summary: Specifies that this portion of the code should not be reachable in standard operation. If it is reached an exception is generated.
			result.setException(new Diagnostic("Reached unreachable code", Position.INTRINSIC));
			return;
		}));

		this.declareGlobal("range", new NativeFunction(this.FunctionPrototype, List.of("min", "max"), (args, scope, result) -> {
			if (args.size() == 1) {
				args = ensureArgumentTypes(args, List.of("length"), List.of(Primitive.Number.class), scope, result);
				if (result.label != null) return;

				var length = (int) args.get(0).getNumberValue();
				if (length < 0) {
					result.setException(new Diagnostic("Length cannot be < 0", Position.INTRINSIC));
					return;
				}

				result.value = ManagedArray.fromImmutableList(this.ArrayPrototype, new RangeList(length));
				return;
			}

			args = ensureArgumentTypes(args, List.of("min", "max"), List.of(Primitive.Number.class, Primitive.Number.class), scope, result);
			if (result.label != null) return;

			var min = (int) args.get(0).getNumberValue();
			var max = (int) args.get(1).getNumberValue();
			if (max < min) {
				result.setException(new Diagnostic("Argument max cannot be < min", Position.INTRINSIC));
				return;
			}

			result.value = ManagedArray.fromImmutableList(this.ArrayPrototype, new RangeList(min, max));
		}));

		this.declareGlobal("@if", new NativeFunction(this.FunctionPrototype, Collections.emptyList(), (args, scope, result) -> {
			// @summary[[Selects expressions to execute based on predicates. Expected arguments
			// follow a repeating pattern of `condition` + `result`, where the `condition`
			// expression should return a {@link Boolean} or a value that can be converted to such.
			// These pairs are evaluated in order, where if the result of the `condition` expression
			// is `true`, the `result` expression is evaluated and returned. Otherwise the
			// evaluation of the `result` is skipped and the next pair is evaluated. Optionally, a
			// fallback expression may be added as the last argument, which will be evaluated and
			// returned if no conditions return `true`.]]

			var emitter = args.getLast().getNativeValue(BytecodeEmitter.class);
			var position = emitter.nextPosition;

			var endLabel = emitter.getNextLabel() + "_end_if";

			for (int i = 0; i < args.size() - 1; i += 2) {
				if (args.size() - i - 1 < 2) {
					var elseValue = ensureExpression(args.get(i), result);
					if (result.label != null) return;

					emitter.compile(elseValue, result);
					if (result.label != null) return;

					emitter.label(endLabel);

					result.value = Primitive.VOID;
					return;
				}

				var predicate = ensureExpression(args.get(i), result);
				if (result.label != null) return;
				var thenValue = ensureExpression(args.get(i + 1), result);
				if (result.label != null) return;

				emitter.compile(predicate, result);
				if (result.label != null) return;

				var elseLabel = emitter.getNextLabel() + "_else";
				emitter.emit(new BytecodeInstruction.Conditional(elseLabel, false, position));
				emitter.compile(thenValue, result);
				if (result.label != null) return;
				emitter.emit(new BytecodeInstruction.Jump(endLabel));
				emitter.label(elseLabel);
			}

			// If there is no final else clause, return `void`
			emitter.emit(Primitive.VOID);
			emitter.label(endLabel);

			result.value = Primitive.VOID;
		}));

		this.declareGlobal("@while", NativeFunction.simple(this.globalScope, List.of("predicate", "body", "@"), List.of(Expression.class, Expression.class, BytecodeEmitter.class), (args, scope, result) -> {
			// @summary[[Repeatedly evaluates the `predicate` expression, which is expected to
			// return a {@link Boolean} or be convertible to such. If `true` is returned, the `body`
			// expression is executed, otherwise the cycle is terminated.]]
			var predicate = args.get(0).getNativeValue(Expression.class);
			var body = args.get(1).getNativeValue(Expression.class);
			if (result.label != null) return;

			var emitter = args.getLast().getNativeValue(BytecodeEmitter.class);
			var position = emitter.nextPosition;

			var continueLabel = emitter.getNextLabel() + "_continue";
			var breakLabel = emitter.getNextLabel() + "_end_while";

			emitter.label(continueLabel);
			emitter.compile(predicate, result);
			if (result.label != null) return;
			emitter.emit(new BytecodeInstruction.Conditional(breakLabel, false, position));
			emitter.compile(body, result);
			if (result.label != null) return;
			emitter.label(breakLabel);

			emitter.emit(Primitive.VOID);

			result.value = Primitive.VOID;
		}));

		this.declareGlobal("return", NativeFunction.simple(this.globalScope, List.of("value?"), (args, scope, result) -> {
			// @summary: Aborts the execution of the current function, optionally retuning the provided value.
			result.value = args.isEmpty() ? Primitive.VOID : args.get(0);
			result.label = LABEL_RETURN;
		}));

		this.declareGlobal("goto", NativeFunction.simple(this.globalScope, List.of("label"), List.of(Primitive.String.class), (args, scope, result) -> {
			// @summary[[Switches execution to a label with the specified name. This label must be
			// in a block that is at the same level as this invocation or in a parent block that is
			// still in the same function.]]
			result.value = Primitive.VOID;
			result.label = args.get(0).getStringValue();
		}));
	}
}
