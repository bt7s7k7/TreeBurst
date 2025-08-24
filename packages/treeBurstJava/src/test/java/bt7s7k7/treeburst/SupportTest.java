package bt7s7k7.treeburst;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import bt7s7k7.treeburst.support.Primitive;

class SupportTest {
	@Test
	public void primitiveEquality() {
		assertTrue(Primitive.from(0.58).equals(Primitive.from(0.58)));
		assertTrue(Primitive.from(true).equals(Primitive.from(true)));
		assertTrue(Primitive.from("value").equals(Primitive.from("val" + "ue")));
		assertFalse(Primitive.from("value").equals(Primitive.NULL));
		assertFalse(Primitive.from(0.58).equals(Primitive.VOID));
	}
}
