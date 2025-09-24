package bt7s7k7.treeburst.standard;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.findProperty;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureString;

import java.util.List;

import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.ManagedMap;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.ManagedTable;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class TableApi extends LazyTable {
	// @summary[[Represents an object with properties.]]

	public TableApi(ManagedObject prototype, GlobalScope globalScope) {
		super(prototype, globalScope);
	}

	@Override
	protected void initialize() {
		this.declareProperty("prototype", this.globalScope.TablePrototype);

		this.declareProperty("new", NativeFunction.simple(this.globalScope, List.of("this", "properties?"), (args, scope, result) -> {
			// @summary: Creates a new object that inherits from this table's `prototype` property, assuming it exists. Otherwise an exception is generated.
			var self = args.get(0);

			if (!findProperty(self, self, "prototype", scope, result)) {
				result.setException(new Diagnostic("Cannot find a prototype on receiver", Position.INTRINSIC));
				return;
			}

			var prototype = result.value;
			if (!(prototype instanceof ManagedTable prototype_1)) {
				result.setException(new Diagnostic("Prototype must be a Table", Position.INTRINSIC));
				return;
			}

			var table = new ManagedTable(prototype_1);

			if (args.size() == 2) {
				if (args.get(1) instanceof ManagedArray entries) {
					int index = -1;
					for (var element : entries) {
						index++;

						if (!(element instanceof ManagedArray kv) || kv.getLength() > 2) {
							result.setException(new Diagnostic("Entry at index " + index + " is not a pair", Position.INTRINSIC));
							return;
						}

						// In this case, either the key or value was set to void, so don't add this entry
						if (kv.getLength() < 2) continue;

						var key = ensureString(kv.get(0), scope, result).value;
						if (result.label != null) return;

						var value = kv.get(1);

						// If the property already exists overwrite it
						if (!table.declareProperty(key, value)) {
							table.setOwnProperty(key, value);
						}
					}
				} else {
					args = ensureArgumentTypes(args, List.of("this", "properties"), List.of(ManagedTable.class, ManagedMap.class), scope, result);
					if (result.label != null) return;

					var entries = args.get(1).getMapValue();

					for (var kv : entries) {
						var key = ensureString(kv.getKey(), scope, result).value;
						if (result.label != null) return;

						var value = kv.getValue();

						// If the property already exists overwrite it
						if (!table.declareProperty(key, value)) {
							table.setOwnProperty(key, value);
						}
					}
				}
			}

			result.value = table;
		}));

		this.declareProperty("getProperty", NativeFunction.simple(this.globalScope, List.of("object", "property", "receiver?"), List.of(ManagedValue.class, Primitive.String.class, ManagedValue.class), (args, scope, result) -> {
			// @summary[[If the `object` contains property named `property`, returns its value.
			// Otherwise returns {@link void}. If the `receiver` arguments is specified, it will be
			// provided to any possible getters.]]
			var object = args.get(0);
			var property = args.get(1).getStringValue();
			var receiver = args.size() > 2 ? args.get(2) : object;

			if (!findProperty(receiver, object, property, scope, result)) {
				result.value = Primitive.VOID;
			}
		}));
	}

}
