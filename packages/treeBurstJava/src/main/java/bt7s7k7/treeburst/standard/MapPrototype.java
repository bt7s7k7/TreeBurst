package bt7s7k7.treeburst.standard;

import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.ManagedMap;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.ManagedValueUtils;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class MapPrototype extends LazyTable {
	// @summary: Allows the storage of an unordered set of entries, indexed by a key, which may be any type of value.

	public MapPrototype(ManagedObject prototype, GlobalScope globalScope) {
		super(prototype, globalScope);
	}

	@Override
	protected void initialize() {
		this.declareProperty(OperatorConstants.OPERATOR_AT, NativeFunction.simple(this.globalScope, List.of("this", "index", "value?"), (args, scope, result) -> {
			// @summary: Gets or sets an entry in the map. When writing, if the `value` is {@link void}, the selected entry is deleted. When reading, if the selected entry does not exist, a {@link void} is returned.
			if (args.size() <= 2) {
				args = ensureArgumentTypes(args, List.of("this", "index"), List.of(ManagedMap.class, ManagedValue.class), scope, result);
				if (result.label != null) return;

				var self = args.get(0).getMapValue();
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

				var self = args.get(0).getMapValue();
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

		this.declareProperty("clone", NativeFunction.simple(this.globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			// @summary: Creates a copy of the map.
			var self = args.get(0).getMapValue();
			result.value = new ManagedMap(self.prototype, new HashMap<>(self.entries));
		}));

		this.declareProperty("clear", NativeFunction.simple(this.globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			// @summary: Removes all entries in the map.
			var self = args.get(0).getMapValue();
			self.entries.clear();
			result.value = Primitive.VOID;
		}));

		this.declareProperty("keys", NativeFunction.simple(this.globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			// @summary: Returns an array, containing keys of all the entries in the map.
			var self = args.get(0).getMapValue();
			result.value = new ManagedArray(this.globalScope.ArrayPrototype, new ArrayList<>(self.entries.keySet()));
		}));

		this.declareProperty("values", NativeFunction.simple(this.globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			// @summary: Returns an array, containing values of all the entries in the map.
			var self = args.get(0).getMapValue();
			result.value = new ManagedArray(this.globalScope.ArrayPrototype, new ArrayList<>(self.entries.values()));
		}));

		this.declareProperty("entries", NativeFunction.simple(this.globalScope, List.of("this"), List.of(ManagedMap.class), (args, scope, result) -> {
			// @summary: Returns an array, containing all the entries in the map.
			var self = args.get(0).getMapValue();
			var entries = new ManagedArray(this.globalScope.ArrayPrototype, new ArrayList<>(self.entries.size()));

			for (var kv : self.entries.entrySet()) {
				entries.elements.add(new ManagedArray(this.globalScope.ArrayPrototype, List.of(kv.getKey(), kv.getValue())));
			}

			result.value = entries;
		}));

		this.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(this.globalScope, List.of("this", "depth?"), List.of(ManagedMap.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary: Formats the map into a textual form.
			var self = args.get(0).getMapValue();
			var depth = args.size() > 1 ? args.get(1).getNumberValue() : 0;

			if (depth > 0) {
				var dump = ManagedValueUtils.dumpCollection(
						self.getNameOrInheritedName(), true, "{", "}",
						self.entries.entrySet(), Map.Entry::getKey, null, Map.Entry::getValue, (int) depth - 1, scope, result);
				if (dump == null) return;
				result.value = Primitive.from(dump);
				return;
			}

			var header = self.toString();
			result.value = Primitive.from(header);
		}));
	}

}
