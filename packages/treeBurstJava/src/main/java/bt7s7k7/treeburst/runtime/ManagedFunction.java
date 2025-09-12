package bt7s7k7.treeburst.runtime;

import java.util.List;

import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Primitive;

public abstract class ManagedFunction extends ManagedObject { // @symbol: Function
	// @summary[[Represents a repeatedly executable subprogram, that may take input in the forms of arguments and may return a single value.]]
	public abstract void invoke(List<ManagedValue> args, Scope scope, ExpressionResult result);

	@Override
	public ManagedValue getOwnProperty(String name) {
		if (name.equals("name")) {
			return Primitive.from(this.name);
		}

		return super.getOwnProperty(name);
	}

	@Override
	public String toString() {
		return "function " + (this.name == null ? "" : this.name) + "(" + String.join(", ", this.parameters) + ")";
	}

	public final List<String> parameters;

	public ManagedFunction(ManagedObject prototype, List<String> parameters) {
		super(prototype);
		this.parameters = parameters;
	}
}
