package bt7s7k7.treeburst.parsing;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ExpressionVisitor {
	public Expression visit(Expression expression) {
		return expression.applyChangesToChildren(this);
	}

	public List<Expression> visitList(List<Expression> list) {
		ArrayList<Expression> result = null;

		for (int i = 0; i < list.size(); i++) {
			var element = list.get(i);
			var visited = this.visit(element);
			if (result != null) {
				result.add(visited);
				continue;
			}

			if (element == visited) continue;

			result = new ArrayList<>(list.size());
			result.addAll(list.subList(0, i));
			result.add(visited);
		}

		return result == null ? list : result;
	}

	public List<Map.Entry<Expression, Expression>> visitEntryList(List<Map.Entry<Expression, Expression>> list) {
		ArrayList<Map.Entry<Expression, Expression>> result = null;

		for (int i = 0; i < list.size(); i++) {
			var kv = list.get(i);

			var key = this.visit(kv.getKey());
			var value = this.visit(kv.getValue());

			var unchanged = key == kv.getKey() && value == kv.getValue();

			if (result != null) {
				if (unchanged) {
					result.add(kv);
				} else {
					result.add(new AbstractMap.SimpleEntry<>(key, value));
				}

				continue;
			}

			if (unchanged) continue;

			result = new ArrayList<>(list.size());
			result.addAll(list.subList(0, i));
			result.add(new AbstractMap.SimpleEntry<>(key, value));
		}

		return result == null ? list : result;
	}

	public Expression tryVisit(Expression expression) {
		if (expression == null) return null;
		return this.visit(expression);
	}
}
