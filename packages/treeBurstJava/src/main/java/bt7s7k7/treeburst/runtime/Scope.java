package bt7s7k7.treeburst.runtime;

import java.util.HashMap;
import java.util.Map;

public class Scope {
	public final Map<String, Variable> variables = new HashMap<>();
	public final Scope parent;
	public final Realm realm;

	public Scope(Scope parent, Realm realm) {
		this.parent = parent;
		this.realm = realm;
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

	public Variable getOrDeclareLocal(String name) {
		return this.variables.computeIfAbsent(name, __ -> new Variable());
	}

	public Scope makeChild() {
		return new Scope(this, this.realm);
	}
}
