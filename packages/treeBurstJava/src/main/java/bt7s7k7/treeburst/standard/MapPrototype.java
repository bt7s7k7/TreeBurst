package bt7s7k7.treeburst.standard;

import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.ManagedMap;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class MapPrototype extends LazyTable {

	public MapPrototype(ManagedObject prototype, GlobalScope globalScope) {
		super(prototype, globalScope);
	}

	@Override
	protected void initialize() {
		this.declareProperty(OperatorConstants.OPERATOR_AT, NativeFunction.simple(globalScope, List.of("this", "index", "value?"), (args, scope, result) -> {
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
					result.setException(new Diagnostic("Cannot set a map entry with a void key", Position.INTRINSIC));
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

		this.declareProperty("clone", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			result.value = new ManagedMap(self.prototype, new HashMap<>(self.entries));
		}));

		this.declareProperty("clear", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			self.entries.clear();
			result.value = Primitive.VOID;
		}));

		this.declareProperty("keys", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			result.value = new ManagedArray(this.globalScope.ArrayPrototype, new ArrayList<>(self.entries.keySet()));
		}));

		this.declareProperty("values", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			result.value = new ManagedArray(this.globalScope.ArrayPrototype, new ArrayList<>(self.entries.values()));
		}));

		this.declareProperty("entries", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			var self = (ManagedMap) args.get(0);
			var entries = new ManagedArray(this.globalScope.ArrayPrototype, new ArrayList<>(self.entries.size()));

			for (var kv : self.entries.entrySet()) {
				entries.elements.add(new ManagedArray(this.globalScope.ArrayPrototype, List.of(kv.getKey(), kv.getValue())));
			}

			result.value = entries;
		}));
	}

}
