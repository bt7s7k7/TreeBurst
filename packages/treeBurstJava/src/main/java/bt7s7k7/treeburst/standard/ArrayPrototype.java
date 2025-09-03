package bt7s7k7.treeburst.standard;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.ManagedValueUtils;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class ArrayPrototype extends LazyTable {

	public ArrayPrototype(ManagedObject prototype, GlobalScope globalScope) {
		super(prototype, globalScope);
	}

	@Override
	protected void initialize() {
		this.declareProperty(OperatorConstants.OPERATOR_AT, NativeFunction.simple(this.globalScope, List.of("this", "index", "value?"), (args, scope, result) -> {
			if (args.size() <= 2) {
				args = ensureArgumentTypes(args, List.of("this", "index"), List.of(ManagedArray.class, Primitive.Number.class), scope, result);
				if (result.label != null) return;

				var self = args.get(0).getArrayValue();
				var index = (int) args.get(1).getNumberValue();

				index = self.normalizeIndex(index, result);
				if (result.label != null) return;

				result.value = self.elements.get(index);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "index", "value"), List.of(ManagedArray.class, Primitive.Number.class, ManagedValue.class), scope, result);
				if (result.label != null) return;

				var self = args.get(0).getArrayValue();
				var index = (int) args.get(1).getNumberValue();
				var value = args.get(2);

				if (value == Primitive.VOID) {
					result.setException(new Diagnostic("Cannot set an array element to void", Position.INTRINSIC));
					return;
				}

				index = self.normalizeIndex(index, result);
				if (result.label != null) return;

				self.elements.set(index, value);
				result.value = value;
			}
		}));

		this.declareProperty("truncate", NativeFunction.simple(this.globalScope, List.of("this", "length"), List.of(ManagedArray.class, Primitive.Number.class), (args, scope, result) -> {
			var self = args.get(0).getArrayValue();
			var length = (int) args.get(1).getNumberValue();

			if (length < 0) {
				result.setException(new Diagnostic("Cannot set array length to be less than zero", Position.INTRINSIC));
				return;
			}

			if (length < self.elements.size()) {
				self.elements.subList(length, self.elements.size()).clear();
			} else if (length > self.elements.size()) {
				self.elements.addAll(Collections.nCopies(length - self.elements.size(), Primitive.NULL));
			}

			return;
		}));

		this.declareProperty("clone", NativeFunction.simple(this.globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = args.get(0).getArrayValue();
			result.value = new ManagedArray(self.prototype, new ArrayList<>(self.elements));
		}));

		this.declareProperty("clear", NativeFunction.simple(this.globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = args.get(0).getArrayValue();
			self.elements.clear();
			result.value = Primitive.VOID;
		}));

		this.declareProperty("slice", NativeFunction.simple(this.globalScope, List.of("this", "from", "to?"), (args, scope, result) -> {
			if (args.size() == 2) {
				args = ensureArgumentTypes(args, List.of("this", "from"), List.of(ManagedArray.class, Primitive.Number.class), scope, result);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "from", "to"), List.of(ManagedArray.class, Primitive.Number.class, Primitive.Number.class), scope, result);
			}

			if (result.label != null) return;
			var self = args.get(0).getArrayValue();
			var from = (int) args.get(1).getNumberValue();
			var to = args.size() == 2 ? self.elements.size() : (int) args.get(2).getNumberValue();

			from = self.normalizeIndex(from, result);
			if (result.label != null) return;

			to = self.normalizeLimit(to, result);
			if (result.label != null) return;

			result.value = new ManagedArray(self.prototype, new ArrayList<>(self.elements.subList(from, to)));
		}));

		this.declareProperty("splice", NativeFunction.simple(this.globalScope, List.of("this", "index", "delete", "insert?"), (args, scope, result) -> {
			if (args.size() == 3) {
				args = ensureArgumentTypes(args, List.of("this", "index", "delete"), List.of(ManagedArray.class, Primitive.Number.class, Primitive.Number.class), scope, result);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "index", "delete", "insert"), List.of(ManagedArray.class, Primitive.Number.class, Primitive.Number.class, ManagedArray.class), scope, result);
			}

			if (result.label != null) return;
			var self = args.get(0).getArrayValue();
			var index = (int) args.get(1).getNumberValue();
			var delete = (int) args.get(2).getNumberValue();
			var insert = args.size() == 3 ? null : args.get(3).getArrayValue();

			index = self.normalizeLimit(index, result);
			if (result.label != null) return;

			if (index + delete > self.elements.size()) {
				result.setException(new Diagnostic("Too many elements to delete, deleting " + delete + " at index " + index + " in array of size " + self.elements.size(), Position.INTRINSIC));
				return;
			}

			var range = self.elements.subList(index, index + delete);
			range.clear();
			if (insert != null) {
				range.addAll(insert.elements);
			}

			result.value = Primitive.VOID;
		}));

		this.declareProperty("append", NativeFunction.simple(this.globalScope, List.of("this", "elements"), List.of(ManagedArray.class, ManagedArray.class), (args, scope, result) -> {
			var self = args.get(0).getArrayValue();
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(self.elements.size()), Primitive.ZERO, args.get(1)), scope, result);
		}));

		this.declareProperty("prepend", NativeFunction.simple(this.globalScope, List.of("this", "elements"), List.of(ManagedArray.class, ManagedArray.class), (args, scope, result) -> {
			var self = args.get(0).getArrayValue();
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.ZERO, Primitive.ZERO, args.get(1)), scope, result);
		}));

		this.declareProperty("pop", NativeFunction.simple(this.globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = args.get(0).getArrayValue();
			var removedValue = self.elements.size() > 0 ? self.elements.getLast() : Primitive.VOID;

			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(-1), Primitive.from(1)), scope, result);

			if (result.label == null) {
				result.value = removedValue;
			}
		}));

		this.declareProperty("shift", NativeFunction.simple(this.globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = args.get(0).getArrayValue();
			var removedValue = self.elements.size() > 0 ? self.elements.getFirst() : Primitive.VOID;

			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.ZERO, Primitive.from(1)), scope, result);

			if (result.label == null) {
				result.value = removedValue;
			}
		}));

		this.declareProperty("push", new NativeFunction(this.globalScope.FunctionPrototype, List.of("this", "...elements"), (args, scope, result) -> {
			var args_1 = ensureArgumentTypes(args, List.of("this"), List.of(ManagedArray.class), scope, result);
			if (result.label != null) return;

			var self = args_1.get(0).getArrayValue();
			var elementsToAdd = new ManagedArray(this, args.subList(1, args.size()));
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(self.elements.size()), Primitive.ZERO, elementsToAdd), scope, result);
		}));

		this.declareProperty("unshift", new NativeFunction(this.globalScope.FunctionPrototype, List.of("this", "...elements"), (args, scope, result) -> {
			var args_1 = ensureArgumentTypes(args, List.of("this"), List.of(ManagedArray.class), scope, result);
			if (result.label != null) return;

			var self = args_1.get(0).getArrayValue();
			var elementsToAdd = new ManagedArray(this, args.subList(1, args.size()));
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.ZERO, Primitive.ZERO, elementsToAdd), scope, result);
		}));

		this.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(this.globalScope, List.of("this", "depth?"), List.of(ManagedArray.class, Primitive.Number.class), (args, scope, result) -> {
			var self = args.get(0).getArrayValue();
			var depth = args.size() > 1 ? args.get(1).getNumberValue() : 0;

			if (depth > 0) {
				var dump = ManagedValueUtils.<ManagedValue>dumpCollection(
						self.getNameOrInheritedName(), true, "[", "]",
						self.elements, null, null, Function.identity(), (int) depth - 1, scope, result);
				if (dump == null) return;

				result.value = Primitive.from(dump);
				return;
			}

			var header = self.toString();
			result.value = Primitive.from(header);
		}));
	}

}
