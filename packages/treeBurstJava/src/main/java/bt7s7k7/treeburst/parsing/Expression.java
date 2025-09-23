package bt7s7k7.treeburst.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

	public default Expression applyChangesToChildren(ExpressionVisitor visitor) {
		return this;
	}

	public record NumberLiteral(Position position, double value) implements Expression {}

	public record StringLiteral(Position position, String value) implements Expression {}

	public record ArrayLiteral(Position position, List<Expression> elements) implements Expression {
		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var elements = visitor.visitList(this.elements);
			if (elements == this.elements) return this;
			return new ArrayLiteral(this.position, elements);
		}
	}

	public record Identifier(Position position, String name) implements Expression {}

	public record VariableDeclaration(Position position, Expression declaration) implements Expression {
		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var declaration = visitor.tryVisit(this.declaration);
			if (declaration == this.declaration) return this;
			return new VariableDeclaration(this.position, declaration);
		}
	}

	public record Assignment(Position position, Expression receiver, Expression value) implements Expression {
		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var receiver = visitor.tryVisit(this.receiver);
			var value = visitor.tryVisit(this.value);
			if (receiver == this.receiver && value == this.value) return this;
			return new Assignment(this.position, receiver, value);
		}
	}

	public record MemberAccess(Position position, Expression receiver, String member) implements Expression {
		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var receiver = visitor.tryVisit(this.receiver);
			if (receiver == this.receiver) return this;
			return new MemberAccess(this.position, receiver, this.member);
		}
	}

	public record Group(Position position, List<Expression> children) implements Expression {
		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var children = visitor.visitList(this.children);
			if (children == this.children) return this;
			return new Group(this.position, children);
		}
	}

	public record FunctionDeclaration(Position position, List<Parameter> parameters, Expression body) implements Expression {
		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var body = visitor.tryVisit(this.body);
			if (body == this.body) return this;
			return new FunctionDeclaration(this.position, this.parameters, body);
		}
	}

	public record Label(Position position, String name, Expression target) implements Expression {
		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var target = visitor.tryVisit(this.target);
			if (target == this.target) return this;
			return new Label(this.position, this.name, target);
		}
	}

	public record Spread(Position position, Expression target) implements Expression {
		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var target = visitor.tryVisit(this.target);
			if (target == this.target) return this;
			return new Spread(this.position, target);
		}
	}

	public record Placeholder(Position position) implements Expression {}

	public record AdvancedAssignment(Position position, String operator, Expression receiver, Expression value) implements Expression {
		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var receiver = visitor.tryVisit(this.receiver);
			var value = visitor.tryVisit(this.value);
			if (receiver == this.receiver && value == this.value) return this;
			return new AdvancedAssignment(this.position, this.operator, receiver, value);
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
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var target = visitor.tryVisit(this.target);
			var args = visitor.visitList(this.args);
			if (target == this.target && args == this.args) return this;
			return new Invocation(this.position, target, args);
		}
	}

	public class MapLiteral implements Expression {
		private final Position position;

		@Override
		public Position position() {
			return this.position;
		}

		public final List<Map.Entry<Expression, Expression>> entries;

		public MapLiteral(Position position, List<Entry<Expression, Expression>> entries) {
			this.position = position;
			this.entries = entries;
		}

		public MapLiteral(Position position) {
			this.position = position;
			this.entries = new ArrayList<>();
		}

		@Override
		public Expression applyChangesToChildren(ExpressionVisitor visitor) {
			var entries = visitor.visitEntryList(this.entries);
			if (entries == this.entries) return this;
			return new MapLiteral(this.position, entries);
		}
	}
}
