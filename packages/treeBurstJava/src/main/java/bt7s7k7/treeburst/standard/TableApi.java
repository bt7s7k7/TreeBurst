package bt7s7k7.treeburst.standard;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.findProperty;

import java.util.List;

import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.ManagedTable;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.Position;

public class TableApi extends LazyTable {

	public TableApi(ManagedObject prototype, GlobalScope globalScope) {
		super(prototype, globalScope);
	}

	@Override
	protected void initialize() {
		this.declareProperty("prototype", globalScope.TablePrototype);

		this.declareProperty("new", NativeFunction.simple(globalScope, List.of("this"), (args, scope, result) -> {
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

			result.value = new ManagedTable(prototype_1);
		}));
	}

}
