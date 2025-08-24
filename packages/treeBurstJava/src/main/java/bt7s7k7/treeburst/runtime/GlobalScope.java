package bt7s7k7.treeburst.runtime;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.findProperty;
import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.getValueName;
import static bt7s7k7.treeburst.runtime.ExpressionResult.LABEL_EXCEPTION;
import static bt7s7k7.treeburst.support.ManagedValueUtils.verifyArguments;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
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

		if (!this.Table.declareProperty(OperatorConstants.OPERATOR_IS, NativeFunction.simple(globalScope, List.of("this", "other"), (args, scope, result) -> {
			if (!verifyArguments(args, List.of("this", "other"), result)) return;
			result.value = Primitive.from(args.get(0) == args.get(1));
		})));
	}
}
