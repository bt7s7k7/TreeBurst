package bt7s7k7.treeburst.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

import bt7s7k7.treeburst.support.Position;

public interface Expression extends Token {
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

	public record NumberLiteral(Position position, double value) implements Expression {}

	public record StringLiteral(Position position, String value) implements Expression {}

	public record ArrayLiteral(Position position, List<Expression> elements) implements Expression {}

	public record Identifier(Position position, String name) implements Expression {}

	public record VariableDeclaration(Position position, Expression declaration) implements Expression {}

	public record Assignment(Position position, Expression receiver, Expression value) implements Expression {}

	public record MemberAccess(Position position, Expression receiver, String member) implements Expression {}

	public record Group(Position position, List<Expression> children) implements Expression {}

	public record FunctionDeclaration(Position position, List<String> parameters, Expression body) implements Expression {}

	public record Label(Position position, String name, Expression target) implements Expression {}

	public record AdvancedAssignment(Position position, String operator, Expression receiver, Expression value) implements Expression {}

	public record Invocation(Position position, Expression target, List<Expression> args) implements Expression {
		public Invocation withArgument(Expression argument) {
			return new Expression.Invocation(this.position(), this.target(), Streams.concat(this.args().stream(), Stream.of(argument)).toList());
		}

		public static Invocation makeMethodCall(Position position, Expression receiver, String method, List<Expression> args) {
			return new Invocation(position, new MemberAccess(position, receiver, method), args);
		}
	}

	public class MapLiteral implements Expression {
		private final Position position;

		@Override
		public Position position() {
			return this.position;
		}

		public final ArrayList<Map.Entry<Expression, Expression>> entries = new ArrayList<>();

		public MapLiteral(Position position) {
			this.position = position;
		}
	}
}
