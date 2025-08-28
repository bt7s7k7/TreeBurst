package bt7s7k7.treeburst.standard;

import static bt7s7k7.treeburst.runtime.ExpressionEvaluator.evaluateInvocation;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class ArrayPrototype extends LazyTable {

	public ArrayPrototype(ManagedObject prototype, GlobalScope globalScope) {
		super(prototype, globalScope);
	}

	@Override
	protected void initialize() {
		this.declareProperty(OperatorConstants.OPERATOR_AT, NativeFunction.simple(globalScope, List.of("this", "index", "value?"), (args, scope, result) -> {
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
					result.setException(new Diagnostic("Cannot set an array element to void", Position.INTRINSIC));
					return;
				}

				index = self.normalizeIndex(index, result);
				if (result.label != null) return;

				self.elements.set(index, value);
				result.value = value;
			}
		}));

		this.declareProperty("truncate", NativeFunction.simple(globalScope, List.of("this", "length"), List.of(ManagedArray.class, Primitive.Number.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			var length = (int) ((Primitive.Number) args.get(1)).value;

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

		this.declareProperty("clone", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			result.value = new ManagedArray(self.prototype, new ArrayList<>(self.elements));
		}));

		this.declareProperty("clear", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			self.elements.clear();
			result.value = Primitive.VOID;
		}));

		this.declareProperty("slice", NativeFunction.simple(globalScope, List.of("this", "from", "to?"), (args, scope, result) -> {
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

		this.declareProperty("splice", NativeFunction.simple(globalScope, List.of("this", "index", "delete", "insert?"), (args, scope, result) -> {
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

		this.declareProperty("append", NativeFunction.simple(globalScope, List.of("this", "elements"), List.of(ManagedArray.class, ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(self.elements.size()), Primitive.ZERO, args.get(1)), scope, result);
		}));

		this.declareProperty("prepend", NativeFunction.simple(globalScope, List.of("this", "elements"), List.of(ManagedArray.class, ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.ZERO, Primitive.ZERO, args.get(1)), scope, result);
		}));

		this.declareProperty("pop", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			var removedValue = self.elements.size() > 0 ? self.elements.getLast() : Primitive.VOID;

			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(-1), Primitive.from(1)), scope, result);

			if (result.label == null) {
				result.value = removedValue;
			}
		}));

		this.declareProperty("shift", NativeFunction.simple(globalScope, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			var self = (ManagedArray) args.get(0);
			var removedValue = self.elements.size() > 0 ? self.elements.getFirst() : Primitive.VOID;

			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.ZERO, Primitive.from(1)), scope, result);

			if (result.label == null) {
				result.value = removedValue;
			}
		}));

		this.declareProperty("push", new NativeFunction(this.globalScope.FunctionPrototype, List.of("this", "...elements"), (args, scope, result) -> {
			var args_1 = ensureArgumentTypes(args, List.of("this"), List.of(ManagedArray.class), scope, result);
			if (result.label != null) return;

			var self = (ManagedArray) args_1.get(0);
			var elementsToAdd = new ManagedArray(this, args.subList(1, args.size()));
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(self.elements.size()), Primitive.ZERO, elementsToAdd), scope, result);
		}));

		this.declareProperty("unshift", new NativeFunction(this.globalScope.FunctionPrototype, List.of("this", "...elements"), (args, scope, result) -> {
			var args_1 = ensureArgumentTypes(args, List.of("this"), List.of(ManagedArray.class), scope, result);
			if (result.label != null) return;

			var self = (ManagedArray) args_1.get(0);
			var elementsToAdd = new ManagedArray(this, args.subList(1, args.size()));
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.ZERO, Primitive.ZERO, elementsToAdd), scope, result);
		}));
	}

}
