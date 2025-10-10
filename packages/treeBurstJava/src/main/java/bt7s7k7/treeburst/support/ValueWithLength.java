package bt7s7k7.treeburst.support;

import bt7s7k7.treeburst.runtime.ExpressionResult;

public interface ValueWithLength {
	public int getLength();

	public default int normalizeIndex(int index, ExpressionResult result) {
		if (index < 0) {
			index = this.getLength() + index;
		}

		if (index < 0 || index >= this.getLength()) {
			result.setException(new Diagnostic("Index " + index + " out of range of array of length " + this.getLength(), Position.INTRINSIC));
			return 0;
		}

		return index;
	}

	public default int normalizeLimit(int limit, ExpressionResult result) {
		if (limit < 0) {
			limit = this.getLength() + limit;
		}

		if (limit < 0 || limit > this.getLength()) {
			result.setException(new Diagnostic("Index " + limit + " out of range of array of length " + this.getLength(), Position.INTRINSIC));
			return 0;
		}

		return limit;
	}
}
