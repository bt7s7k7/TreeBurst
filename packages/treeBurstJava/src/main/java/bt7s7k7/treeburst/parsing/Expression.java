package bt7s7k7.treeburst.parsing;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

import bt7s7k7.treeburst.support.Parameter;
import bt7s7k7.treeburst.support.Position;

public interface Expression extends Token {
	@Override
	public Position position();

	default String toFormattedString() {
		var result = new StringBuilder();
		var parser = new GenericParser(this.toString());

		var indent = 0;
		while (!parser.isDone()) {
			var content = parser.readUntil((v, i) -> v.startsWith("[", i) || v.startsWith("]", i) || v.startsWith(", ", i)).toString().trim();
			result.append(content);
			if (parser.isDone()) break;

			if (parser.consume("],")) {
				indent--;
				result.append("\n" + "    ".repeat(indent) + "],\n");
				result.append("    ".repeat(indent));
				continue;
			}

			if (parser.consume("]")) {
				indent--;
				result.append("\n" + "    ".repeat(indent) + "]");
				continue;
			}

			if (parser.consume("[")) {
				indent++;
				result.append("[\n" + "    ".repeat(indent));
				continue;
			}

			if (parser.consume(",")) {
				result.append(",\n" + "    ".repeat(indent));
				continue;
			}

		}

		return result.toString();
	}

	@FunctionalInterface
	public static interface Transformer {
		public Expression apply(Expression expression);

		public default boolean canApply(Expression expression) {
			return true;
		}

		public default Expression transform(Expression expression) {
			if (expression == null) return null;
			return expression.transform(this);
		}
	}

	public default Expression transform(Transformer transformer) {
		return transformer.apply(this);
	}

	public record NumberLiteral(Position position, double value) implements Expression {}

	public record StringLiteral(Position position, String value) implements Expression {}

	public record ArrayLiteral(Position position, List<Expression> elements) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new ArrayLiteral(this.position, this.elements.stream().map(transformer::transform).toList()));
		}
	}

	public record Identifier(Position position, String name) implements Expression {}

	public record VariableDeclaration(Position position, Expression declaration) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new VariableDeclaration(this.position, transformer.transform(this.declaration)));
		}
	}

	public record Assignment(Position position, Expression receiver, Expression value) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new Assignment(this.position, transformer.transform(this.receiver), transformer.transform(this.value)));
		}
	}

	public record MemberAccess(Position position, Expression receiver, String member) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new MemberAccess(this.position, transformer.transform(this.receiver), this.member));
		}
	}

	public record Group(Position position, List<Expression> children) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new Group(this.position, this.children.stream().map(transformer::transform).toList()));
		}
	}

	public record FunctionDeclaration(Position position, List<Parameter> parameters, Expression body) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new FunctionDeclaration(this.position, this.parameters, transformer.transform(this.body)));
		}
	}

	public record Label(Position position, String name, Expression target) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new Label(this.position, this.name, transformer.transform(this.target)));
		}
	}

	public record Spread(Position position, Expression target) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new Spread(this.position, transformer.transform(this.target)));
		}
	}

	public record Placeholder(Position position) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new Placeholder(this.position));
		}
	}

	public record AdvancedAssignment(Position position, String operator, Expression receiver, Expression value) implements Expression {
		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new AdvancedAssignment(this.position, this.operator, transformer.transform(this.receiver), transformer.transform(this.value)));
		}
	}

	public record Invocation(Position position, Expression target, List<Expression> args) implements Expression {
		public Invocation withArgument(Expression argument) {
			return new Expression.Invocation(this.position(), this.target(), Streams.concat(this.args().stream(), Stream.of(argument)).toList());
		}

		public Invocation withFirstArgument(Expression argument) {
			return new Expression.Invocation(this.position(), this.target(), Streams.concat(Stream.of(argument), this.args().stream()).toList());
		}

		public static Invocation makeMethodCall(Position position, Expression receiver, String method, List<Expression> args) {
			return new Invocation(position, new MemberAccess(position, receiver, method), args);
		}

		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new Invocation(this.position, transformer.transform(this.target), this.args.stream().map(transformer::transform).toList()));
		}
	}

	public class MapLiteral implements Expression {
		private final Position position;

		@Override
		public Position position() {
			return this.position;
		}

		public final ArrayList<Map.Entry<Expression, Expression>> entries;

		public MapLiteral(Position position, ArrayList<Entry<Expression, Expression>> entries) {
			this.position = position;
			this.entries = entries;
		}

		public MapLiteral(Position position) {
			this.position = position;
			this.entries = new ArrayList<>();
		}

		@Override
		public Expression transform(Transformer transformer) {
			if (!transformer.canApply(this)) return this;
			return transformer.apply(new MapLiteral(this.position, this.entries.stream()
					.map(kv -> new AbstractMap.SimpleEntry<>(transformer.transform(kv.getKey()), transformer.transform(kv.getValue())))
					.collect(Collectors.toCollection(ArrayList::new))));
		}
	}
}
