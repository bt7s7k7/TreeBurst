package bt7s7k7.treeburst.support;

import bt7s7k7.treeburst.runtime.ManagedArray;
import bt7s7k7.treeburst.runtime.ManagedFunction;
import bt7s7k7.treeburst.runtime.ManagedMap;
import bt7s7k7.treeburst.runtime.ManagedTable;
import bt7s7k7.treeburst.runtime.NativeHandle;

public abstract class ManagedValue {
	public double getNumberValue() {
		return ((Primitive.Number) this).value;
	}

	public boolean getBooleanValue() {
		return ((Primitive.Boolean) this).value;
	}

	public String getStringValue() {
		return ((Primitive.String) this).value;
	}

	public ManagedFunction getFunctionValue() {
		return (ManagedFunction) this;
	}

	public ManagedTable getTableValue() {
		return (ManagedTable) this;
	}

	public ManagedArray getArrayValue() {
		return (ManagedArray) this;
	}

	public ManagedMap getMapValue() {
		return (ManagedMap) this;
	}

	public <T> T getNativeValue(Class<T> type) {
		return type.cast(((NativeHandle) this).value);
	}

	public <T> T cast(Class<T> type) {
		if (ManagedValue.class.isAssignableFrom(type)) {
			return type.cast(this);
		} else {
			return this.getNativeValue(type);
		}
	}
}
