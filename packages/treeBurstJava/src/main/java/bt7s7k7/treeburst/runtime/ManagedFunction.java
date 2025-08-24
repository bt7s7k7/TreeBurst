package bt7s7k7.treeburst.runtime;

import java.util.List;

import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Primitive;

public abstract class ManagedFunction extends ManagedObject {
	public abstract void invoke(List<ManagedValue> args, Scope scope, ExpressionResult result);

	@Override
	public boolean getProperty(String name, ExpressionResult result) {
		if (name.equals("name")) {
			result.value = Primitive.from(this.name);
			return true;
		}
		return super.getProperty(name, result);
	}

	@Override
	public String toString() {
		return "[function " + (this.name == null ? "<anon>" : this.name) + "]";
	}

	public final List<String> parameters;

	public ManagedFunction(ManagedObject prototype, List<String> parameters) {
		super(prototype);
		this.parameters = parameters;
	}
}
