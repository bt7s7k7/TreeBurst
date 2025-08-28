package bt7s7k7.treeburst.standard;

import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.ManagedTable;
import bt7s7k7.treeburst.support.ManagedValue;

public abstract class LazyTable extends ManagedTable {
	protected final GlobalScope globalScope;
	protected boolean initialized = false;

	public LazyTable(ManagedObject prototype, GlobalScope globalScope) {
		super(prototype);
		this.globalScope = globalScope;
	}

	protected abstract void initialize();

	@Override
	public boolean getProperty(String name, ExpressionResult result) {
		if (!this.initialized) {
			this.initialized = true;
			this.initialize();
		}

		return super.getProperty(name, result);
	}

	@Override
	public boolean declareProperty(String name, ManagedValue value) {
		if (!this.initialized) {
			this.initialized = true;
			this.initialize();
		}

		return super.declareProperty(name, value);
	}

	@Override
	public boolean setProperty(String name, ManagedValue value) {
		if (!this.initialized) {
			this.initialized = true;
			this.initialize();
		}

		return super.setProperty(name, value);
	}
}
