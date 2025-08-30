package bt7s7k7.treeburst.standard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.ManagedTable;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.runtime.Scope;
import bt7s7k7.treeburst.support.ManagedValue;

public class NativeHandleWrapper<T> {
	public final Class<T> type;

	protected boolean hasGetters = false;
	protected boolean hasSetters = false;
	protected final List<BiConsumer<ManagedTable, GlobalScope>> capabilities = new ArrayList<>();

	public NativeHandleWrapper(Class<T> type) {
		this.type = type;
	}

	public class Prototype extends LazyTable {
		public Prototype(ManagedObject prototype, GlobalScope globalScope) {
			super(prototype, globalScope);
		}

		@Override
		protected void initialize() {
			initializePrototype(this, globalScope);
		}
	}

	@FunctionalInterface
	public interface MethodImplementation<T> {
		public void run(T self, List<ManagedValue> args, Scope scope, ExpressionResult result);
	}

	public NativeHandleWrapper<T> addMethod(String name, List<String> parameters, List<Class<?>> types, MethodImplementation<T> impl) {
		this.capabilities.add((handle, globalScope) -> {
			handle.declareProperty(name, NativeFunction.simple(globalScope,
					Stream.concat(Stream.of("this"), parameters.stream()).toList(),
					Stream.concat(Stream.of(this.type), types.stream()).toList(),
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

	public <TValue> NativeHandleWrapper<T> addProperty(String name, Class<TValue> valueType, Function<T, ManagedValue> getter, BiConsumer<T, TValue> setter) {
		this.addGetter(name, getter);

		this.hasSetters = true;
		return this.addMethod("set_" + name, List.of("value"), List.of(valueType), (self, args, scope, result) -> {
			var newValue = args.get(0);
			setter.accept(self, newValue.cast(valueType));
			result.value = newValue;
		});
	}

	public void initializePrototype(ManagedTable table, GlobalScope globalScope) {
		for (var capability : capabilities) {
			capability.accept(table, globalScope);
		}
	}

	public ManagedTable buildPrototype(GlobalScope globalScope) {
		var prototype = new Prototype(globalScope.TablePrototype, globalScope);
		if (this.hasGetters) prototype.hasGetters = true;
		if (this.hasSetters) prototype.hasSetters = true;
		return prototype;
	}
}
