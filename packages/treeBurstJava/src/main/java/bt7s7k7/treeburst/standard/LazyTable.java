package bt7s7k7.treeburst.standard;

import bt7s7k7.treeburst.runtime.ManagedObject;
import bt7s7k7.treeburst.runtime.ManagedTable;
import bt7s7k7.treeburst.runtime.Realm;
import bt7s7k7.treeburst.support.ManagedValue;

public abstract class LazyTable extends ManagedTable {
	protected final Realm realm;
	protected boolean initialized = false;

	public LazyTable(ManagedObject prototype, Realm realm) {
		super(prototype);
		this.realm = realm;
	}

	protected abstract void initialize();

	@Override
	public ManagedValue getOwnProperty(String name) {
		if (!this.initialized) {
			this.initialized = true;
			this.initialize();
		}

		return super.getOwnProperty(name);
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
	public boolean setOwnProperty(String name, ManagedValue value) {
		if (!this.initialized) {
			this.initialized = true;
			this.initialize();
		}

		return super.setOwnProperty(name, value);
	}
}
