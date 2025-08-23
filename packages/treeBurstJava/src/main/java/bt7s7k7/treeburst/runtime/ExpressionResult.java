package bt7s7k7.treeburst.runtime;

import bt7s7k7.treeburst.support.ManagedValue;
import bt7s7k7.treeburst.support.Primitive;

public class ExpressionResult {
	public ManagedValue value = Primitive.VOID;
	public String label = null;

	public static final String LABEL_RETURN = "!return";
	public static final String LABEL_EXCEPTION = "!exception";
}
