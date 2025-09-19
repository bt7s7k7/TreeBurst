package bt7s7k7.treeburst.runtime;

import java.util.List;

import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Parameter;
import bt7s7k7.treeburst.support.Primitive;

public abstract class ManagedFunction extends ManagedObject { // @symbol: Function
	// @summary[[Represents a repeatedly executable subprogram, that may take input in the forms of arguments and may return a single value.]]

	private List<Parameter> parameters;
	private List<String> parameterNames;

	public List<Parameter> getParameters() {
		if (this.parameters == null) {
			this.parameters = this.parameterNames.stream().map(Parameter::new).toList();
		}

		return this.parameters;
	}

	public List<String> getParameterNames() {
		if (this.parameterNames == null) {
			this.parameterNames = this.parameters.stream().map(v -> v.name).toList();
		}

		return this.parameterNames;
	}

	public boolean hasThisArgument() {
		if (this.parameterNames != null) {
			return !this.parameterNames.isEmpty() && this.parameterNames.get(0).equals("this");
		} else {
			// At least one of these lists must be non-null, so no need to check if parameters is null
			return !this.parameters.isEmpty() && this.parameters.get(0).name.equals("this");
		}
	}

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
		return "function " + (this.name == null ? "" : this.name) + "(" + String.join(", ", this.getParameterNames()) + ")";
	}

	public ManagedFunction(ManagedObject prototype, List<String> parameterNames, List<Parameter> parameters) {
		super(prototype);
		this.parameters = parameters;
		this.parameterNames = parameterNames;
	}
}
