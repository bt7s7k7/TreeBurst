package bt7s7k7.treeburst.runtime;

import java.util.HashMap;
import java.util.Map;

public class Scope {
	public final Map<String, Variable> variables = new HashMap<>();
	public final Scope parent;
	public final GlobalScope globalScope;

	public Scope(Scope parent, GlobalScope globalScope) {
		this.parent = parent;
		this.globalScope = globalScope;
	}

	protected Scope() {
		this.parent = null;
		this.globalScope = (GlobalScope) this;
	}

	public Variable findVariable(String name) {
		var variable = this.variables.get(name);
		if (variable != null) {
			return variable;
		}

		if (this.parent != null) {
			return this.parent.findVariable(name);
		}

		return null;
	}

	public Variable declareVariable(String name) {
		if (this.variables.containsKey(name)) {
			return null;
		}

		var variable = new Variable();
		this.variables.put(name, variable);
		return variable;
	}

	public Scope makeChild() {
		return new Scope(this, this.globalScope);
	}
}
