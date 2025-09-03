package bt7s7k7.treeburst.standard;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.ManagedFunction;
import bt7s7k7.treeburst.runtime.ManagedMap;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.ManagedTable;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.runtime.NativeHandle;
import bt7s7k7.treeburst.runtime.Scope;
import bt7s7k7.treeburst.runtime.Variable;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.ManagedValueUtils;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class NativeHandleWrapper<T> {
	public final Class<T> type;

	protected boolean hasGetters = false;
	protected boolean hasSetters = false;
	protected final List<BiConsumer<ManagedTable, GlobalScope>> capabilities = new ArrayList<>();
	protected String name = null;

	public NativeHandleWrapper(Class<T> type) {
		this.type = type;
	}

	public class Prototype extends LazyTable {
		public Prototype(ManagedObject prototype, GlobalScope globalScope) {
			super(prototype, globalScope);
		}

		@Override
		protected void initialize() {
			NativeHandleWrapper.this.initializePrototype(this, this.globalScope);
		}
	}

	@FunctionalInterface
	public interface MethodImplementation<T> {
		public void run(T self, List<ManagedValue> args, Scope scope, ExpressionResult result);
	}

	public NativeHandleWrapper<T> addMethod(String name, Function<GlobalScope, ManagedFunction> factory) {
		this.capabilities.add((handle, globalScope) -> {
			handle.declareProperty(name, factory.apply(globalScope));
		});

		return this;
	}

	public NativeHandleWrapper<T> addMethod(String name, List<String> parameters, List<Class<?>> types, MethodImplementation<T> impl) {
		this.capabilities.add((handle, globalScope) -> {
			handle.declareProperty(name, NativeFunction.simple(globalScope,
					Stream.concat(Stream.of("this"), parameters.stream()).toList(),
					Stream.concat(Stream.of(this.type), types == null ? Collections.nCopies(parameters.size(), ManagedValue.class).stream() : types.stream()).toList(),
					(args, scope, result) -> {
						var self = args.get(0).getNativeValue(this.type);
						impl.run(self, args.subList(1, args.size()), globalScope, result);
					}));
		});

		return this;
	}

	public NativeHandleWrapper<T> addGetter(String name, Function<T, ManagedValue> getter) {
		this.hasGetters = true;

		return this.addMethod("get_" + name, Collections.emptyList(), Collections.emptyList(), (self, args, scope, result) -> {
			result.value = getter.apply(self);
		});
	}

	public NativeHandleWrapper<T> addGetter(String name, BiFunction<T, Scope, ManagedValue> getter) {
		this.hasGetters = true;

		return this.addMethod("get_" + name, Collections.emptyList(), Collections.emptyList(), (self, args, scope, result) -> {
			result.value = getter.apply(self, scope);
		});
	}

	public <TValue> NativeHandleWrapper<T> addProperty(String name, Class<TValue> valueType, Function<T, ManagedValue> getter, BiConsumer<T, TValue> setter) {
		this.addGetter(name, getter);

		this.hasSetters = true;
		return this.addMethod("set_" + name, List.of("value"), List.of(valueType), (self, args, scope, result) -> {
			var newValue = args.get(0);
			setter.accept(self, newValue.cast(valueType));
			result.value = newValue;
		});
	}

	public <TKey, TValue> NativeHandleWrapper<T> addMapAccess(Function<T, Map<TKey, TValue>> mapGetter,
			Class<?> keyType, Class<?> valueType,
			Function<TKey, ManagedValue> importKey, Function<ManagedValue, TKey> exportKey,
			Function<TValue, ManagedValue> importValue, Function<ManagedValue, TValue> exportValue) {
		return this.addMapAccess(mapGetter, keyType, valueType, (key, __) -> importKey.apply(key), exportKey, (value, __) -> importValue.apply(value), exportValue);
	}

	public <TKey, TValue> NativeHandleWrapper<T> addMapAccess(Function<T, Map<TKey, TValue>> mapGetter,
			Class<?> keyType, Class<?> valueType,
			BiFunction<TKey, Scope, ManagedValue> importKey, Function<ManagedValue, TKey> exportKey,
			BiFunction<TValue, Scope, ManagedValue> importValue, Function<ManagedValue, TValue> exportValue) {

		this.addGetter("length", self -> Primitive.from(mapGetter.apply(self).size()));

		if (exportKey != null && importValue != null) {
			if (exportValue != null) {
				this.addMethod("clear", Collections.emptyList(), Collections.emptyList(), (self, args, scope, result) -> {
					var map = mapGetter.apply(self);
					map.clear();
					result.value = Primitive.VOID;
				});

				this.addMethod(OperatorConstants.OPERATOR_AT, List.of("key", "value?"), List.of(keyType, valueType), (self, args, scope, result) -> {
					var map = mapGetter.apply(self);
					var key = exportKey.apply(args.get(0));

					if (args.size() == 1) {
						var value = map.get(key);

						if (value == null) {
							result.value = Primitive.VOID;
						} else {
							result.value = importValue.apply(value, scope);
						}
					} else {
						var value = exportValue.apply(args.get(1));

						if (value == Primitive.VOID) {
							map.remove(key);
						} else {
							map.put(key, value);
						}

						result.value = args.get(1);
					}
				});
			} else {
				this.addMethod(OperatorConstants.OPERATOR_AT, List.of("key"), List.of(keyType), (self, args, scope, result) -> {
					var map = mapGetter.apply(self);
					var key = exportKey.apply(args.get(0));
					var value = map.get(key);

					if (value == null) {
						result.value = Primitive.VOID;
					} else {
						result.value = importValue.apply(value, scope);
					}
				});
			}

			if (importKey != null) {
				this.addMethod("keys", Collections.emptyList(), Collections.emptyList(), (self, args, scope, result) -> {
					var map = mapGetter.apply(self);
					result.value = new ManagedArray(scope.globalScope.ArrayPrototype, map.keySet().stream().map(v -> importKey.apply(v, scope)).collect(Collectors.toCollection(ArrayList::new)));
				});
			}

			if (importValue != null) {
				this.addMethod("values", Collections.emptyList(), Collections.emptyList(), (self, args, scope, result) -> {
					var map = mapGetter.apply(self);
					result.value = new ManagedArray(scope.globalScope.ArrayPrototype, map.values().stream().map(v -> importValue.apply(v, scope)).collect(Collectors.toCollection(ArrayList::new)));
				});
			}

			if (importValue != null && importKey != null) {
				this.addMethod("entries", Collections.emptyList(), Collections.emptyList(), (self, args, scope, result) -> {
					var map = mapGetter.apply(self);
					result.value = new ManagedArray(scope.globalScope.ArrayPrototype, map.entrySet().stream()
							.map(kv -> new ManagedArray(scope.globalScope.ArrayPrototype, List.of(importKey.apply(kv.getKey(), scope), importValue.apply(kv.getValue(), scope))))
							.collect(Collectors.toCollection(ArrayList::new)));
				});

				this.addMethod("map", Collections.emptyList(), Collections.emptyList(), (self, args, scope, result) -> {
					var map = mapGetter.apply(self);
					var managedMap = new ManagedMap(scope.globalScope.MapPrototype);

					for (var kv : map.entrySet()) {
						managedMap.entries.put(importKey.apply(kv.getKey(), scope), importValue.apply(kv.getValue(), scope));
					}

					result.value = managedMap;
				});

				this.addDumpMethod((self, depth, scope, result) -> {
					var map = mapGetter.apply(self);
					return ManagedValueUtils.dumpCollection(
							this.name, true, "{", "}",
							map.entrySet(), v -> importKey.apply(v.getKey(), scope), null, v -> importValue.apply(v.getValue(), scope), depth - 1, scope, result);
				});

				this.capabilities.add((handle, globalScope) -> {
					handle.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(globalScope, List.of("this", "depth?"), List.of(this.type, Primitive.Number.class), (args, scope, result) -> {
						var self = args.get(0);
						evaluateInvocation(self, self, "map", Position.INTRINSIC, Collections.emptyList(), scope, result);
						if (result.label != null) return;
						var map = result.value;
						evaluateInvocation(map, map, OperatorConstants.OPERATOR_DUMP, Position.INTRINSIC, args.subList(1, args.size()), scope, result);
					}));
				});
			}
		}

		return this;
	}

	public NativeHandleWrapper<T> addName(String name) {
		this.name = name;
		this.capabilities.add((handle, globalScope) -> {
			handle.name = name;
		});

		return this;
	}

	@FunctionalInterface
	public static interface DumpMethodImpl<T> {
		public String get(T self, int depth, Scope scope, ExpressionResult result);
	}

	public NativeHandleWrapper<T> addDumpMethod(DumpMethodImpl<T> dumpGetter) {
		this.addMethod(OperatorConstants.OPERATOR_DUMP, List.of("depth?"), List.of(Primitive.Number.class), (self, args, scope, result) -> {
			var dump = dumpGetter.get(self, (int) args.get(0).getNumberValue(), scope, result);
			if (dump == null) return;
			result.value = Primitive.from(dump);
		});

		return this;
	}

	public void initializePrototype(ManagedTable table, GlobalScope globalScope) {
		for (var capability : this.capabilities) {
			capability.accept(table, globalScope);
		}
	}

	public ManagedTable buildPrototype(GlobalScope globalScope) {
		var prototype = new Prototype(globalScope.TablePrototype, globalScope);
		if (this.hasGetters) prototype.hasGetters = true;
		if (this.hasSetters) prototype.hasSetters = true;
		return prototype;
	}

	public ManagedTable ensurePrototype(GlobalScope globalScope) {
		if (this.name == null) throw new NullPointerException("Cannot cache prototype without a name");

		// We need to check if a prototype was already created and if so, we need to make sure it
		// valid. We don't actually check if the prototype has correct values, since this is way too
		// expensive.
		Variable classVariable = null;

		do {
			classVariable = globalScope.findVariable(this.name);
			// Variable not defined, need to define
			if (classVariable == null) break;
			// Variable defined, but not correct, need to overwrite
			if (!(classVariable.value instanceof ManagedTable classTable)) break;
			var prototypeValue = classTable.getOwnProperty("prototype");
			// Variable does not have a prototype, need to overwrite
			if (prototypeValue != null && prototypeValue instanceof ManagedTable prototype) return prototype;
		} while (false);

		var prototype = this.buildPrototype(globalScope);

		if (classVariable == null) {
			// Variable is not declared
			classVariable = globalScope.declareVariable(this.name);
		}

		// Overwrite or define variable
		classVariable.value = new ManagedTable(globalScope.TablePrototype, Map.of("prototype", prototype));

		return prototype;
	}

	public NativeHandle getHandle(T value, GlobalScope globalScope) {
		return new NativeHandle(this.ensurePrototype(globalScope), value);
	}
}
