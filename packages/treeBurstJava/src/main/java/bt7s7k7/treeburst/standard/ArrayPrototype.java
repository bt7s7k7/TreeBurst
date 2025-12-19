package bt7s7k7.treeburst.standard;

import static bt7s7k7.treeburst.runtime.EvaluationUtil.evaluateInvocation;
import static bt7s7k7.treeburst.support.ManagedValueUtils.BINARY_OPERATOR_PARAMETERS;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureArgumentTypes;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureBoolean;
import static bt7s7k7.treeburst.support.ManagedValueUtils.ensureString;
import static bt7s7k7.treeburst.support.ManagedValueUtils.prepareBinaryOperator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import bt7s7k7.treeburst.bytecode.ArgumentStack;
import bt7s7k7.treeburst.bytecode.BytecodeEmitter;
import bt7s7k7.treeburst.bytecode.BytecodeInstruction;
import bt7s7k7.treeburst.bytecode.ProgramFragment;
import bt7s7k7.treeburst.bytecode.ValueStack;
import bt7s7k7.treeburst.parsing.Expression;
import bt7s7k7.treeburst.parsing.OperatorConstants;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.ManagedFunction;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.NativeFunction;
import bt7s7k7.treeburst.runtime.Realm;
import bt7s7k7.treeburst.runtime.Scope;
import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.ManagedValueUtils;
import bt7s7k7.treeburst.support.Position;
import bt7s7k7.treeburst.support.Primitive;

public class ArrayPrototype extends LazyTable {
	// @summary: Allows the storage of an ordered list of elements, each indexed by a number starting from `0`.

	public ArrayPrototype(ManagedObject prototype, Realm realm) {
		super(prototype, realm);
	}

	private static class ArrayForEachInstruction implements BytecodeInstruction {
		public final ProgramFragment body;
		public final String elementVariable;
		public final String indexVariable;
		public final String arrayVariable;
		public final Position position;

		public ArrayForEachInstruction(ProgramFragment body, String elementVariable, String indexVariable, String arrayVariable, Position position) {
			this.body = body;
			this.elementVariable = elementVariable;
			this.indexVariable = indexVariable;
			this.arrayVariable = arrayVariable;
			this.position = position;
		}

		@Override
		public int executeInstruction(ValueStack values, ArgumentStack arguments, Scope scope, ExpressionResult result) {
			var arrayValue = values.pop();

			if (!(arrayValue instanceof ManagedArray array)) {
				result.setException(new Diagnostic("Receiver is not an array", this.position));
				return STATUS_BREAK;
			}

			var indexVariable = this.indexVariable == null ? null : scope.getOrDeclareLocal(this.indexVariable);
			var elementVariable = this.elementVariable == null ? null : scope.getOrDeclareLocal(this.elementVariable);

			if (this.arrayVariable != null) {
				scope.getOrDeclareLocal(this.arrayVariable).value = array;
			}

			var i = 0;
			for (var element : array) {
				if (indexVariable != null) indexVariable.value = Primitive.from(i++);
				if (elementVariable != null) elementVariable.value = element;

				this.body.evaluate(0, values, arguments, scope, result);
				if (result.label != null) return STATUS_BREAK;
			}

			values.push(arrayValue);
			return STATUS_NORMAL;
		}

		@Override
		public String toString() {
			return this.position.format(
					"-- start ArrayForEach element = " + this.elementVariable
							+ ", index = " + this.indexVariable
							+ ", array = " + this.arrayVariable,
					"") + "\n"
					+ this.body.toString()
					+ "\n-- end ArrayForEach";
		}

	}

	@Override
	protected void initialize() {
		this.declareProperty(OperatorConstants.OPERATOR_AT, NativeFunction.simple(this.realm, List.of("this", "index", "value?"), (args, scope, result) -> {
			// @summary[[Gets or sets an element at the requested index. If an index outside the
			// array is specified, an exception is generated. As always, the index may be
			// negative to index from the end of the array, where `-1` is the last element and so
			// on.]]
			ManagedArray self;
			int index;
			ManagedValue value = null;

			if (args.size() <= 2) {
				args = ensureArgumentTypes(args, List.of("this", "index"), List.of(ManagedArray.class, Primitive.Number.class), scope, result);
				if (result.label != null) return;

				self = args.get(0).getArrayValue();
				index = (int) args.get(1).getNumberValue();
			} else {
				args = ensureArgumentTypes(args, List.of("this", "index", "value"), List.of(ManagedArray.class, Primitive.Number.class, ManagedValue.class), scope, result);
				if (result.label != null) return;

				self = args.get(0).getArrayValue();
				index = (int) args.get(1).getNumberValue();
				value = args.get(2);

				if (value == Primitive.VOID) {
					result.setException(new Diagnostic("Cannot set an array element to void", Position.INTRINSIC));
					return;
				}
			}

			index = self.normalizeIndex(index, result);
			if (result.label != null) return;

			if (value == null) {
				result.value = self.get(index);
			} else {
				self.set(index, value);
				result.value = value;
			}
		}));

		this.declareProperty("tryAt", NativeFunction.simple(this.realm, List.of("this", "index", "value?"), (args, scope, result) -> {
			// @summary[[Gets or sets an element at the requested index. If an index outside the
			// array is specified, it returns {@link void} when reading or the length of array is
			// extended on writing (all added elements are filled with {@link null}). As always, the
			// index may be negative to index from the end of the array, where `-1` is the
			// last element and so on.]]
			ManagedArray self;
			int index;
			ManagedValue value = null;

			if (args.size() <= 2) {
				args = ensureArgumentTypes(args, List.of("this", "index"), List.of(ManagedArray.class, Primitive.Number.class), scope, result);
				if (result.label != null) return;

				self = args.get(0).getArrayValue();
				index = (int) args.get(1).getNumberValue();
			} else {
				args = ensureArgumentTypes(args, List.of("this", "index", "value"), List.of(ManagedArray.class, Primitive.Number.class, ManagedValue.class), scope, result);
				if (result.label != null) return;

				self = args.get(0).getArrayValue();
				index = (int) args.get(1).getNumberValue();
				value = args.get(2);

				if (value == Primitive.VOID) {
					result.setException(new Diagnostic("Cannot set an array element to void", Position.INTRINSIC));
					return;
				}
			}

			if (index < 0) {
				index = self.getLength() + index;

				if (index < 0) {
					result.setException(new Diagnostic("Cannot use negative indices for an array", Position.INTRINSIC));
					return;
				}
			}

			if (value == null) {
				if (index >= self.getLength()) {
					result.value = Primitive.VOID;
					return;
				}

				result.value = self.get(index);
			} else {
				if (index >= self.getLength()) {
					self.getElementsMutable().addAll(Collections.nCopies(index + 1 - self.getLength(), Primitive.NULL));
				}

				self.set(index, value);
				result.value = value;
			}
		}));

		this.declareProperty("truncate", NativeFunction.simple(this.realm, List.of("this", "length"), List.of(ManagedArray.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary[[Sets the length of the array to the provided value. If the new length is
			// shorter, elements at the end are discarded. If the length is longer, new elements are
			// filled with {@link null}. Returns a reference to this array.]]
			var self = args.get(0).getArrayValue();
			var length = (int) args.get(1).getNumberValue();

			if (length < 0) {
				result.setException(new Diagnostic("Cannot set array length to be less than zero", Position.INTRINSIC));
				return;
			}

			if (length < self.getLength()) {
				self.getElementsMutable().subList(length, self.getLength()).clear();
			} else if (length > self.getLength()) {
				if (self instanceof ManagedArray.ListBackedArray listBacked && listBacked.getLength() == 0) {
					listBacked.immutable = true;
					listBacked.elements = Collections.nCopies(length - self.getLength(), Primitive.NULL);
				} else {
					self.getElementsMutable().addAll(Collections.nCopies(length - self.getLength(), Primitive.NULL));
				}
			}

			result.value = self;
		}));

		this.declareProperty("clone", NativeFunction.simple(this.realm, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			// @summary: Creates a copy of the array.
			var self = args.get(0).getArrayValue();
			result.value = self.makeCopy();
		}));

		this.declareProperty("clear", NativeFunction.simple(this.realm, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			// @summary: Removes all elements in the array.
			var self = args.get(0).getArrayValue();
			self.clear();
			result.value = Primitive.VOID;
		}));

		this.declareProperty("slice", NativeFunction.simple(this.realm, List.of("this", "from", "to?"), (args, scope, result) -> {
			// @summary[[Creates a copy of a section of the array starting at `from` and ending at
			// `to` (or the end of the array if not provided). As always, the index may be
			// negative to index from the end of the array, where `-1` is the last element and so
			// on.]]
			if (args.size() == 2) {
				args = ensureArgumentTypes(args, List.of("this", "from"), List.of(ManagedArray.class, Primitive.Number.class), scope, result);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "from", "to"), List.of(ManagedArray.class, Primitive.Number.class, Primitive.Number.class), scope, result);
			}

			if (result.label != null) return;
			var self = args.get(0).getArrayValue();
			var from = (int) args.get(1).getNumberValue();
			var to = args.size() == 2 ? self.getLength() : (int) args.get(2).getNumberValue();

			from = self.normalizeIndex(from, result);
			if (result.label != null) return;

			to = self.normalizeLimit(to, result);
			if (result.label != null) return;

			result.value = self.makeView(from, to).makeCopy();
		}));

		this.declareProperty("view", NativeFunction.simple(this.realm, List.of("this", "from", "to?"), (args, scope, result) -> {
			// @summary[[Creates a view of a section of the array starting at `from` and ending at
			// `to` (or the end of the array if not provided). As always, the index may be
			// negative to index from the end of the array, where `-1` is the last element and so
			// on.]]
			if (args.size() == 2) {
				args = ensureArgumentTypes(args, List.of("this", "from"), List.of(ManagedArray.class, Primitive.Number.class), scope, result);
			} else {
				args = ensureArgumentTypes(args, List.of("this", "from", "to"), List.of(ManagedArray.class, Primitive.Number.class, Primitive.Number.class), scope, result);
			}

			if (result.label != null) return;
			var self = args.get(0).getArrayValue();
			var from = (int) args.get(1).getNumberValue();
			var to = args.size() == 2 ? self.getLength() : (int) args.get(2).getNumberValue();

			from = self.normalizeIndex(from, result);
			if (result.label != null) return;

			to = self.normalizeLimit(to, result);
			if (result.label != null) return;

			result.value = self.makeView(from, to);
		}));

		this.declareProperty("splice", NativeFunction.simple(this.realm, List.of("this", "index", "delete", "insert?"), (args, scope, result) -> {
			// @summary[[Removes a section of the array at `index` of length `delete`. Optionally
			// replacing this section with the elements of `insert`. If the `delete` argument is
			// `0`, this function is equivalent to an insertion function; in this case the `index`
			// may point to just after the end of the array. As always, the index may be
			// negative to index from the end of the array, where `-1` is the last element and so
			// on.]]
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

			if (index + delete > self.getLength()) {
				result.setException(new Diagnostic("Too many elements to delete, deleting " + delete + " at index " + index + " in array of size " + self.getLength(), Position.INTRINSIC));
				return;
			}

			var range = self.getElementsMutable().subList(index, index + delete);
			range.clear();
			if (insert != null) {
				range.addAll(insert.getElementsReadOnly());
			}

			result.value = Primitive.VOID;
		}));

		this.declareProperty("append", NativeFunction.simple(this.realm, List.of("this", "elements"), List.of(ManagedArray.class, ManagedArray.class), (args, scope, result) -> {
			// @summary: Appends the provided elements to the end of the array. Returns the current array.
			var self = args.get(0).getArrayValue();
			var elements = args.get(1);
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(self.getLength()), Primitive.ZERO, elements), scope, result);
			result.value = self;
		}));

		this.declareProperty("prepend", NativeFunction.simple(this.realm, List.of("this", "elements"), List.of(ManagedArray.class, ManagedArray.class), (args, scope, result) -> {
			// @summary: Prepends the provided elements before the start of the array. Returns the current array.
			var self = args.get(0).getArrayValue();
			var elements = args.get(1);
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.ZERO, Primitive.ZERO, elements), scope, result);
			result.value = self;
		}));

		this.declareProperty("pop", NativeFunction.simple(this.realm, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			// @summary: Removes the last element of the array and returns it. If the array is empty returns {@link void}.
			var self = args.get(0).getArrayValue();
			var removedValue = self.getLength() > 0 ? self.get(self.getLength() - 1) : Primitive.VOID;

			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(-1), Primitive.from(1)), scope, result);
			if (result.label != null) return;

			result.value = removedValue;
		}));

		this.declareProperty("shift", NativeFunction.simple(this.realm, List.of("this"), List.of(ManagedArray.class), (args, scope, result) -> {
			// @summary: Removes the first element of the array and returns it. If the array is empty returns {@link void}.
			var self = args.get(0).getArrayValue();
			var removedValue = self.getLength() > 0 ? self.get(0) : Primitive.VOID;

			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.ZERO, Primitive.from(1)), scope, result);
			if (result.label != null) return;

			result.value = removedValue;
		}));

		this.declareProperty("push", new NativeFunction(this.realm.FunctionPrototype, List.of("this", "...elements"), (args, scope, result) -> {
			// @summary: Adds an element after the end of the array. Returns the last added value.
			var args_1 = ensureArgumentTypes(args, List.of("this"), List.of(ManagedArray.class), scope, result);
			if (result.label != null) return;

			var self = args_1.get(0).getArrayValue();

			if (args.size() == 1) {
				// If there are no elements to add, don't even call splice
				result.value = Primitive.VOID;
				return;
			}

			var elementsToAdd = ManagedArray.withElements(this, args.subList(1, args.size()));
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.from(self.getLength()), Primitive.ZERO, elementsToAdd), scope, result);
			result.value = args.getLast();
		}));

		this.declareProperty("unshift", new NativeFunction(this.realm.FunctionPrototype, List.of("this", "...elements"), (args, scope, result) -> {
			// @summary: Adds an element before the start of the array. Returns the last added value.
			var args_1 = ensureArgumentTypes(args, List.of("this"), List.of(ManagedArray.class), scope, result);
			if (result.label != null) return;

			var self = args_1.get(0).getArrayValue();

			if (args.size() == 1) {
				// If there are no elements to add, don't even call splice
				result.value = Primitive.VOID;
				return;
			}

			var elementsToAdd = ManagedArray.withElements(this, args.subList(1, args.size()));
			evaluateInvocation(self, self, "splice", Position.INTRINSIC, List.of(Primitive.ZERO, Primitive.ZERO, elementsToAdd), scope, result);
			result.value = args.getLast();
		}));

		this.declareProperty(OperatorConstants.OPERATOR_DUMP, NativeFunction.simple(this.realm, List.of("this", "depth?"), List.of(ManagedArray.class, Primitive.Number.class), (args, scope, result) -> {
			// @summary: Formats the array into a textual form.
			var self = args.get(0).getArrayValue();
			var depth = args.size() > 1 ? args.get(1).getNumberValue() : 0;

			if (depth > 0) {
				var dump = ManagedValueUtils.<ManagedValue>dumpCollection(
						self.getNameOrInheritedName(), true, "[", "]",
						self.getElementsReadOnly(), null, null, Function.identity(), (int) depth - 1, scope, result);
				if (dump == null) return;

				result.value = Primitive.from(dump);
				return;
			}

			var header = self.toString();
			result.value = Primitive.from(header);
		}));

		this.declareProperty("map", NativeFunction.simple(this.realm, List.of("this", "function"), List.of(ManagedArray.class, ManagedFunction.class), (args, scope, result) -> {
			// @summary[[Creates a new array with the results of calling `function` on every
			// element. The function is called with `value` of the element, the `index` of the
			// element and a reference to this `array`. If `function` returns {@link void}, the
			// element is discarded and the resulting array is shorter.]]
			var self = args.get(0).getArrayValue();
			var function = args.get(1).getFunctionValue();

			var output = ManagedArray.withCapacity(this.realm.ArrayPrototype, self.getLength());
			var outputElements = output.getElementsMutable();

			for (int i = 0; i < self.getLength(); i++) {
				var element = self.get(i);

				evaluateInvocation(Primitive.VOID, Primitive.VOID, function, Position.INTRINSIC, List.of(element, Primitive.from(i), self), scope, result);
				if (result.label != null) return;

				var resultElement = result.value;
				if (result.value == Primitive.VOID) continue;

				outputElements.add(resultElement);
			}

			result.value = output;
		}));

		this.declareProperty("foreach", NativeFunction.simple(this.realm, List.of("this", "function"), List.of(ManagedArray.class, ManagedFunction.class), (args, scope, result) -> {
			// @summary[[Calls a function for every element of this array. The function is called
			// with `value` of the element, the `index` of the element and a reference to this
			// `array`. Returns this array.]]
			var self = args.get(0).getArrayValue();
			var function = args.get(1).getFunctionValue();

			for (int i = 0; i < self.getLength(); i++) {
				var element = self.get(i);

				evaluateInvocation(Primitive.VOID, Primitive.VOID, function, Position.INTRINSIC, List.of(element, Primitive.from(i), self), scope, result);
				if (result.label != null) return;
			}

			result.value = self;
		}));

		this.declareProperty("filter", NativeFunction.simple(this.realm, List.of("this", "function"), List.of(ManagedArray.class, ManagedFunction.class), (args, scope, result) -> {
			// @summary[[Creates a new array with only the elements for which `function` returned
			// `true`. The function is called with `value` of the element, the `index` of the
			// element and a reference to this `array`.]]
			var self = args.get(0).getArrayValue();
			var function = args.get(1).getFunctionValue();

			var output = ManagedArray.empty(this.realm.ArrayPrototype);
			var outputElements = output.getElementsMutable();

			for (int i = 0; i < self.getLength(); i++) {
				var element = self.get(i);

				evaluateInvocation(Primitive.VOID, Primitive.VOID, function, Position.INTRINSIC, List.of(element, Primitive.from(i), self), scope, result);
				if (result.label != null) return;

				var resultElement = ensureBoolean(result.value, scope, result);
				if (result.label != null) return;

				if (resultElement.value) {
					outputElements.add(element);
				}
			}

			result.value = output;
		}));

		this.declareProperty("@foreach", NativeFunction.simple(this.realm, List.of("this", "function", "@"), List.of(Expression.class, Expression.FunctionDeclaration.class, BytecodeEmitter.class), (args, scope, result) -> {
			// @summary[[Equivalent to the {@link Array.prototype.foreach} function, except this
			// macro inlines the `function`, allowing you to share the scope and use control flow
			// functions like {@link return} and {@link goto}.]]
			var self = args.get(0).getNativeValue(Expression.class);
			var functionDeclaration = args.get(1).getNativeValue(Expression.FunctionDeclaration.class);

			var emitter = args.get(2).getNativeValue(BytecodeEmitter.class);
			var position = emitter.nextPosition;

			emitter.compile(self, result);
			if (result.label != null) return;

			String elementName = null;
			String indexName = null;
			String arrayName = null;

			var parameters = functionDeclaration.parameters();
			if (parameters.size() > 0) elementName = parameters.get(0).name;
			if (parameters.size() > 1) indexName = parameters.get(1).name;
			if (parameters.size() > 2) arrayName = parameters.get(2).name;

			var fragment = new ProgramFragment(functionDeclaration.body());
			fragment.compile(scope, result);
			if (result.label != null) return;

			emitter.emit(new ArrayForEachInstruction(fragment, elementName, indexName, arrayName, position));

			result.value = Primitive.VOID;
		}));

		this.declareProperty("join", NativeFunction.simple(this.realm, List.of("this", "separator"), List.of(ManagedArray.class, Primitive.String.class), (args, scope, result) -> {
			// @summary[[Creates a {@link String} as that is a concatenation of all elements of the
			// array. Elements that are not strings are converted to strings using their `k_string`
			// method.]]

			var self = args.get(0).getArrayValue();
			var separator = args.get(1).getStringValue();

			var builder = new StringBuilder();

			var first = true;
			for (var element : self) {
				if (first) {
					first = false;
				} else {
					builder.append(separator);
				}

				var string = ensureString(element, scope, result);
				if (result.label != null) return;
				builder.append(string.value);
			}

			result.value = Primitive.from(builder.toString());
		}));

		this.declareProperty(OperatorConstants.OPERATOR_ADD, NativeFunction.simple(this.realm, BINARY_OPERATOR_PARAMETERS, (args, scope, result) -> {
			// @summary[[Creates a new array that is a concatenation of the two input arrays.]]
			var operands = prepareBinaryOperator(OperatorConstants.OPERATOR_ADD, ManagedArray.class, ManagedArray.class, args, scope, result);
			if (result.label != null) return;

			var left = operands.left().getArrayValue();
			var right = operands.right().getArrayValue();
			var output = new ArrayList<ManagedValue>(left.getLength() + right.getLength());

			output.addAll(left.getElementsReadOnly());
			output.addAll(right.getElementsReadOnly());

			result.value = ManagedArray.fromMutableList(scope.realm.ArrayPrototype, output);
		}));

		this.declareProperty(OperatorConstants.OPERATOR_MUL, NativeFunction.simple(this.realm, BINARY_OPERATOR_PARAMETERS, (args, scope, result) -> {
			// @summary[[Creates a new array that is the input array repeated `n` times.]]
			var operands = prepareBinaryOperator(OperatorConstants.OPERATOR_ADD, ManagedArray.class, Primitive.Number.class, args, scope, result);
			if (result.label != null) return;

			var left = operands.left().getArrayValue();
			var elements = left.getElementsReadOnly();
			var right = (int) operands.right().getNumberValue();
			var output = new ArrayList<ManagedValue>(left.getLength() * right);

			for (int i = 0; i < right; i++) {
				output.addAll(elements);
			}

			result.value = ManagedArray.fromMutableList(scope.realm.ArrayPrototype, output);
		}));
	}
}
