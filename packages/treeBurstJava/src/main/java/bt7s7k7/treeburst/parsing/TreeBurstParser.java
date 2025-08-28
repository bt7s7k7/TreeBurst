package bt7s7k7.treeburst.parsing;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.InputDocument;
import bt7s7k7.treeburst.support.Position;

public class TreeBurstParser extends GenericParser {
	public enum OperatorType {
		INVOCATION, ASSIGNMENT, MEMBER_ACCESS, VARIABLE_DECLARATION
	}

	public static class Operator {
		public final int precedence;
		public final OperatorType type;
		public final String name;
		public int resultPrecedence;

		public Operator(int precedence, String name) {
			this.precedence = precedence;
			this.type = OperatorType.INVOCATION;
			this.name = name;
			this.resultPrecedence = this.precedence + 1;
		}

		public Operator(int precedence, OperatorType type) {
			this.precedence = precedence;
			this.type = type;
			this.name = null;
			this.resultPrecedence = this.precedence + 1;
		}

		public Operator withResultPrecedence(int value) {
			this.resultPrecedence = value;
			return this;
		}
	}

	public static class OperatorInstance implements Token {
		public final Position position;
		public final String token;

		public OperatorInstance(Position position, String token) {
			this.position = position;
			this.token = token;
		}
	}

	public List<Diagnostic> diagnostics = new ArrayList<>();

	protected boolean _skippedNewline = false;
	protected int _lastSkippedIndex = -1;
	protected Token _token = null;

	private static final String _UNEXPECTED_EOF = "Unexpected end of input";
	private static final String _INVALID_TOKEN = "Invalid token";

	private static final Map<String, Operator> _PREFIX_OPERATORS;
	private static final Map<String, Operator> _INFIX_OPERATORS;
	private static final List<String> _OPERATOR_TOKENS;

	static {
		_PREFIX_OPERATORS = new LinkedHashMap<>();
		_PREFIX_OPERATORS.put("-", new Operator(10, OperatorConstants.OPERATOR_NEG));
		_PREFIX_OPERATORS.put("~", new Operator(10, OperatorConstants.OPERATOR_BIT_NEG));
		_PREFIX_OPERATORS.put("!", new Operator(10, OperatorConstants.OPERATOR_NOT));
		_PREFIX_OPERATORS.put("+", new Operator(10, OperatorConstants.OPERATOR_NUMBER));
		_PREFIX_OPERATORS.put("!!", new Operator(10, OperatorConstants.OPERATOR_BOOLEAN));
		_PREFIX_OPERATORS.put("$", new Operator(20, OperatorType.VARIABLE_DECLARATION));

		_INFIX_OPERATORS = new LinkedHashMap<>();
		_INFIX_OPERATORS.put("=", new Operator(0, OperatorType.ASSIGNMENT).withResultPrecedence(0));
		_INFIX_OPERATORS.put("&&", new Operator(1, OperatorConstants.OPERATOR_AND));
		_INFIX_OPERATORS.put("||", new Operator(1, OperatorConstants.OPERATOR_OR));
		_INFIX_OPERATORS.put("??", new Operator(1, OperatorConstants.OPERATOR_COALESCE));
		_INFIX_OPERATORS.put("else", new Operator(1, OperatorConstants.OPERATOR_ELSE));
		_INFIX_OPERATORS.put("<", new Operator(2, OperatorConstants.OPERATOR_LT));
		_INFIX_OPERATORS.put("<=", new Operator(2, OperatorConstants.OPERATOR_LTE));
		_INFIX_OPERATORS.put(">", new Operator(2, OperatorConstants.OPERATOR_GT));
		_INFIX_OPERATORS.put(">=", new Operator(2, OperatorConstants.OPERATOR_GTE));
		_INFIX_OPERATORS.put("==", new Operator(2, OperatorConstants.OPERATOR_EQ));
		_INFIX_OPERATORS.put("is", new Operator(2, OperatorConstants.OPERATOR_IS));
		_INFIX_OPERATORS.put("!=", new Operator(2, OperatorConstants.OPERATOR_NEQ));
		_INFIX_OPERATORS.put("^", new Operator(3, OperatorConstants.OPERATOR_BIT_XOR));
		_INFIX_OPERATORS.put("&", new Operator(3, OperatorConstants.OPERATOR_BIT_AND));
		_INFIX_OPERATORS.put("|", new Operator(3, OperatorConstants.OPERATOR_BIT_OR));
		_INFIX_OPERATORS.put("<<", new Operator(4, OperatorConstants.OPERATOR_BIT_SHL));
		_INFIX_OPERATORS.put(">>", new Operator(4, OperatorConstants.OPERATOR_BIT_SHR));
		_INFIX_OPERATORS.put(">>>", new Operator(4, OperatorConstants.OPERATOR_BIT_SHR_UNSIGNED));
		_INFIX_OPERATORS.put("+", new Operator(5, OperatorConstants.OPERATOR_ADD));
		_INFIX_OPERATORS.put("-", new Operator(5, OperatorConstants.OPERATOR_SUB));
		_INFIX_OPERATORS.put("*", new Operator(6, OperatorConstants.OPERATOR_MUL));
		_INFIX_OPERATORS.put("/", new Operator(6, OperatorConstants.OPERATOR_DIV));
		_INFIX_OPERATORS.put("%", new Operator(6, OperatorConstants.OPERATOR_MOD));
		_INFIX_OPERATORS.put("**", new Operator(7, OperatorConstants.OPERATOR_POW).withResultPrecedence(5));
		_INFIX_OPERATORS.put(".", new Operator(100, OperatorType.MEMBER_ACCESS));

		_OPERATOR_TOKENS = Stream.concat(_PREFIX_OPERATORS.keySet().stream(), _INFIX_OPERATORS.keySet().stream())
				.distinct()
				.sorted((a, b) -> b.length() - a.length())
				.collect(Collectors.toList());
	}

	public TreeBurstParser(InputDocument document) {
		super(document.content);
		this.document = document;
	}

	public final InputDocument document;

	public Position getPosition() {
		return new Position(this.document, this.index, 1);
	}

	public Position getPosition(int index) {
		return new Position(this.document, index, this.index - index);
	}

	public void createDiagnostic(String message) {
		createDiagnostic(message, getPosition());
	}

	public void createDiagnostic(String message, int index) {
		createDiagnostic(message, getPosition(index));
	}

	public void createDiagnostic(String message, Position position) {
		var diagnostic = new Diagnostic(message, position);
		this.diagnostics.add(diagnostic);
	}

	public void invalidToken() {
		if (diagnostics.isEmpty()) {
			createDiagnostic(_INVALID_TOKEN);
			return;
		}

		var lastDiagnostic = diagnostics.get(diagnostics.size() - 1);
		if (lastDiagnostic.message.equals(_INVALID_TOKEN) && lastDiagnostic.position.getIndex() + lastDiagnostic.position.getLength() == this.index) {
			lastDiagnostic.position.setLength(lastDiagnostic.position.getLength() + 1);
		}
	}

	public String consumeWord() {
		var start = this.index;
		var prefix = new StringBuilder();
		if (this.consume("@")) {
			prefix.append("@");
		}

		if (this.getCurrent() == ':') {
			this.index = start;
			return "";
		}

		var word = this.readWhile((v, i) -> Character.isLetterOrDigit(v.at(i)) || v.at(i) == '_' || v.at(i) == ':');
		if (word.endsWith(":")) {
			this.index--;
			word = word.substring(0, word.length() - 1);
		}

		if (word.isEmpty()) {
			this.index = start;
			return "";
		}

		return prefix.append(word).toString();
	}

	public void skipWhitespace() {
		if (this.index == this._lastSkippedIndex) {
			return;
		}

		var currSkippedNewLine = false;
		while (!this.isDone()) {
			this.skipWhile((v, i) -> v.at(i) == ' ' || v.at(i) == '\t');
			if (this.isDone()) {
				break;
			}

			var currentChar = this.getCurrent();
			if (currentChar == '\n' || currentChar == '\r') {
				currSkippedNewLine = true;
				this.index++;
				continue;
			}

			if (this.consume("//")) {
				this.skipWhile((v, i) -> v.at(i) != '\n');
				continue;
			}

			if (this.consume("/*")) {
				int depth = 1;
				while (!this.isDone() && depth > 0) {
					if (this.consume("/*")) {
						depth++;
						continue;
					}
					if (this.consume("*/")) {
						depth--;
						continue;
					}
					this.index++;
				}
				continue;
			}

			break;
		}

		this._lastSkippedIndex = this.index;
		this._skippedNewline = currSkippedNewLine;
	}

	public String parseEscapeSequence() {
		if (this.index >= this.input.length()) {
			this.createDiagnostic(_UNEXPECTED_EOF);
			return "";
		}

		var e = this.input.at(this.index);
		this.index++;

		if (e == 'x') {
			int charStart = this.index;
			this.index += 2;

			if (this.index > this.input.length()) {
				this.createDiagnostic(_UNEXPECTED_EOF);
				return "";
			}

			var hexString = this.input.substring(charStart, this.index).toString();
			try {
				int charValue = Integer.parseInt(hexString, 16);
				return String.valueOf((char) charValue);
			} catch (NumberFormatException ex) {
				this.createDiagnostic(_UNEXPECTED_EOF);
				return "\0";
			}
		}

		switch (e) {
			case 'n':
				return "\n";
			case 'r':
				return "\r";
			case 't':
				return "\t";
			case '\'':
				return "'";
			case '\"':
				return "\"";
			case '`':
				return "`";
			case '\\':
				return "\\";
			case '$':
				return "$";
			default:
				this.createDiagnostic("Invalid escape sequence");
				return "\\" + e;
		}
	}

	public Expression.StringLiteral parseString(String term) {
		var value = new StringBuilder();
		var start = this.index - 1;

		while (!this.isDone()) {
			var c = this.getCurrent();
			this.index++;

			if (String.valueOf(c).equals(term)) break;
			if (c == '\\') {
				value.append(this.parseEscapeSequence());
				continue;
			}
			value.append(c);
		}

		return new Expression.StringLiteral(this.getPosition(start), value.toString());
	}

	public Token peekToken(boolean operatorOnly) {
		if (this._token == null) {
			return this.nextToken(operatorOnly);
		}
		return this._token;
	}

	public void parseEnumerated(String[] terms, BooleanSupplier callback) {
		this._token = null;
		while (true) {
			this.skipWhitespace();
			if (this.isDone()) break;

			if (this.consume(",")) continue;

			if (terms.length > 0) {
				var foundTerm = false;
				for (var term : terms) {
					if (this.consume(term)) {
						foundTerm = true;
						break;
					}
				}
				if (foundTerm) return;
			}

			if (!callback.getAsBoolean()) {
				this.invalidToken();
				this.index++;
				continue;
			}
		}
	}

	public List<Expression> parseBlock(String... terms) {
		var result = new ArrayList<Expression>();

		parseEnumerated(terms, () -> {
			if (this.peekToken(false) == null) {
				return false;
			}

			Expression expression = this.parseExpression();
			if (expression != null) {
				result.add(expression);
			}

			return true;
		});

		return result;
	}

	public Token nextToken(boolean operatorOnly) {
		this.skipWhitespace();
		var skippedNewline = this._skippedNewline;

		if (this.isDone()) {
			return this._token = null;
		}

		var start = this.index;
		for (var token : _OPERATOR_TOKENS) {
			if (this.consume(token)) {
				if (token.equals("-") && (Character.isLetterOrDigit(this.getCurrent()) || this.getCurrent() == '_')) {
					this.index--;
					continue;
				}
				return this._token = new OperatorInstance(this.getPosition(start), token);
			}
		}

		if (this.consume("(")) {
			this._token = new Expression.Group(this.getPosition(start), this.parseBlock(")"));
			this._skippedNewline = skippedNewline;
			return this._token;
		}

		if (this.consume("[")) {
			this._token = new Expression.ArrayLiteral(this.getPosition(start), this.parseBlock("]"));
			this._skippedNewline = skippedNewline;
			return this._token;
		}

		if (this.consume("\"")) return this._token = this.parseString("\"");
		if (this.consume("\'")) return this._token = this.parseString("\'");
		if (this.consume("`")) return this._token = this.parseString("`");

		if (operatorOnly) return this._token = null;

		if (this.consume("{")) {
			var map = new Expression.MapLiteral(this.getPosition(start));

			this.parseEnumerated(new String[] { "}" }, () -> {
				var entryStart = this.index;
				Expression key = null;
				String staticName = null;
				Expression value = null;

				if (this.consume("[")) {
					var keyExpressionPosition = this.getPosition();
					var content = this.parseBlock("]");

					if (content.isEmpty()) {
						return false;
					} else {
						key = content.size() == 1 ? content.get(0) : new Expression.Group(keyExpressionPosition, content);
					}
				} else {
					staticName = this.consumeWord();
					if (staticName.isEmpty()) {
						return false;
					} else {
						key = new Expression.StringLiteral(this.getPosition(entryStart), staticName);
					}
				}

				this.skipWhitespace();

				if (this.consume(":")) {
					this.skipWhitespace();
					this._token = null;

					var parseResult = this.parseExpression();
					if (parseResult == null) {
						return false;
					}

					value = parseResult;
				} else if (staticName != null && key != null) {
					value = new Expression.Identifier(key.position(), staticName);
				} else {
					return false;
				}

				if (key == null) return true;
				map.entries.add(new AbstractMap.SimpleEntry<>(key, value));
				return true;
			});
			return this._token = map;
		}

		if (Character.isDigit(this.getCurrent()) || (this.getCurrent() == '-' && Character.isDigit(this.at(1)))) {
			StringBuilder numberText = new StringBuilder();
			if (this.consume("-")) numberText.append("-");
			numberText.append(this.readWhile((v, i) -> Character.isDigit(v.at(i))));

			if (this.consume(".")) {
				numberText.append(".");
				numberText.append(this.readWhile((v, i) -> Character.isDigit(v.at(i))));
			}

			try {
				double number = Double.parseDouble(numberText.toString());
				return this._token = new Expression.NumberLiteral(this.getPosition(start), number);
			} catch (NumberFormatException e) {
				this.createDiagnostic("Invalid number", start);
				return this._token = null;
			}
		}

		if (this.consume("\\")) {
			var parameters = new ArrayList<String>();

			if (this.consume("(")) {
				while (!this.isDone()) {
					this.skipWhitespace();
					if (this.consume(")")) break;
					if (this.consume(",")) continue;
					var parameter = this.consumeWord();
					if (parameter.isEmpty()) {
						this.createDiagnostic("Expected parameter");
					} else {
						parameters.add(parameter);
					}
				}
			}

			this.skipWhitespace();

			Expression body;
			if (this.consume("{")) {
				int bodyStart = this.index;
				body = new Expression.Group(this.getPosition(bodyStart), this.parseBlock("}"));
			} else {
				this._token = null;
				Expression expression = this.parseExpression();
				if (expression == null) return null;
				body = expression;
			}

			return this._token = new Expression.FunctionDeclaration(this.getPosition(start), parameters, body);
		}

		var identifierName = this.consumeWord();
		if (identifierName.isEmpty()) return this._token = null;

		if (this.consume(":")) {
			var labelPosition = this.getPosition(start);
			this.skipWhitespace();
			this._token = null;

			Expression target = null;
			if (!this.isDone() && this.matches(List.of(")", "}", "]")) == null) {
				target = this.parseExpression();
				if (target == null) return null;
			}

			return this._token = new Expression.Label(labelPosition, identifierName, target);
		}

		return this._token = new Expression.Identifier(this.getPosition(start), identifierName);
	}

	public Expression parseExpression() {
		return this.parseExpression(0);
	}

	public Expression parseExpression(int precedence) {
		Token targetToken = this.peekToken(false);
		if (targetToken == null) {
			this.createDiagnostic(this.isDone() ? _UNEXPECTED_EOF : _INVALID_TOKEN);
			return null;
		}

		Expression target;
		if (targetToken instanceof OperatorInstance opInstance) {
			var prefixOperator = _PREFIX_OPERATORS.get(opInstance.token);
			if (prefixOperator == null) {
				this.createDiagnostic("Unexpected operator", opInstance.position);
				this.nextToken(false);
				return null;
			} else {
				this.nextToken(false);
				var operand = this.parseExpression(prefixOperator.resultPrecedence);
				if (operand == null) return null;

				if (prefixOperator.type == OperatorType.VARIABLE_DECLARATION) {
					target = new Expression.VariableDeclaration(opInstance.position, operand);
				} else {
					target = Expression.Invocation.makeMethodCall(opInstance.position, operand, prefixOperator.name, new ArrayList<>());
				}
			}
		} else {
			this.nextToken(true);
			target = (Expression) targetToken;
		}

		while (true) {
			var next = this.peekToken(true);
			if (next instanceof OperatorInstance nextOpInstance) {
				var infixOperator = _INFIX_OPERATORS.get(nextOpInstance.token);
				if (infixOperator == null) return target;

				if (infixOperator.precedence >= precedence) {
					this.nextToken(false);

					var operand = this.parseExpression(infixOperator.resultPrecedence);
					if (operand == null) return target;

					if (infixOperator.type == OperatorType.INVOCATION) {
						target = Expression.Invocation.makeMethodCall(nextOpInstance.position, target, infixOperator.name, Collections.singletonList(operand));
					} else if (infixOperator.type == OperatorType.MEMBER_ACCESS) {
						if (operand instanceof Expression.Identifier identifier) {
							target = new Expression.MemberAccess(operand.position(), target, identifier.name());
						} else {
							this.createDiagnostic("Expected member name");
							return target;
						}
					} else if (infixOperator.type == OperatorType.ASSIGNMENT) {
						if (target instanceof Expression.Invocation invocation) {
							target = invocation.withArgument(operand);
							continue;
						}

						target = new Expression.Assignment(nextOpInstance.position, target, operand);
					}
				} else {
					return target;
				}
			} else if (!this._skippedNewline && next instanceof Expression.Group group) {
				if (precedence > 100) return target;
				target = new Expression.Invocation(target.position(), target, group.children());
				this.nextToken(true);
			} else if (!this._skippedNewline && next instanceof Expression.ArrayLiteral arrayLiteral) {
				if (precedence > 100) return target;
				target = Expression.Invocation.makeMethodCall(target.position(), target, OperatorConstants.OPERATOR_AT, arrayLiteral.elements());
				this.nextToken(true);
			} else if (!this._skippedNewline && next instanceof Expression.StringLiteral stringLiteral) {
				if (precedence > 100) return target;

				if (target instanceof Expression.Invocation invocation) {
					target = invocation.withArgument(stringLiteral);
				} else {
					target = new Expression.Invocation(stringLiteral.position(), target, List.of(stringLiteral));
				}

				this.nextToken(true);
			} else {
				return target;
			}
		}
	}

	public Expression.Group parse() {
		return new Expression.Group(this.getPosition(), this.parseBlock());
	}

}
