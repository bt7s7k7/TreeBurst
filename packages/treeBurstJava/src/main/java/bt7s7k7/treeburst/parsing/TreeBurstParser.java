package bt7s7k7.treeburst.parsing;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import bt7s7k7.treeburst.support.Diagnostic;
import bt7s7k7.treeburst.support.InputDocument;
import bt7s7k7.treeburst.support.Parameter;
import bt7s7k7.treeburst.support.Position;

public class TreeBurstParser extends GenericParser {
	public enum OperatorType {
		INVOCATION, ASSIGNMENT, PIPELINE, MEMBER_ACCESS, VARIABLE_DECLARATION, SPECIAL_SYNTAX
	}

	public static class Operator {
		public final int precedence;
		public final OperatorType type;
		public final String name;
		public int resultPrecedence;
		public boolean isNameWord = false;

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

		public Operator setIsNameWord() {
			this.isNameWord = true;
			return this;
		}
	}

	public static class OperatorInstance implements Token {
		public final Position position;
		public final String token;
		public final boolean isAdvancedAssignment;

		public OperatorInstance(Position position, String token) {
			this.position = position;
			this.token = token;
			this.isAdvancedAssignment = false;
		}

		protected OperatorInstance(Position position, String token, boolean isAdvancedAssignment) {
			this.position = position;
			this.token = token;
			this.isAdvancedAssignment = isAdvancedAssignment;
		}

		public static OperatorInstance makeAdvancedAssignment(Position position, String token) {
			return new OperatorInstance(position, token, true);
		}

		@Override
		public Position position() {
			return this.position;
		}
	}

	public List<Diagnostic> diagnostics = new ArrayList<>();

	protected boolean _skippedNewline = false;
	protected int _lastSkippedIndex = -1;
	protected int _tokenStart = 0;
	protected Token _token = null;

	private static final String _UNEXPECTED_EOF = "Unexpected end of input";
	private static final String _INVALID_TOKEN = "Invalid token";

	private static final Map<String, Operator> _PREFIX_OPERATORS;
	private static final Map<String, Operator> _INFIX_OPERATORS;
	private static final List<String> _OPERATOR_TOKENS;
	private static final Set<String> _WORD_OPERATOR_TOKENS;

	static {
		_PREFIX_OPERATORS = new LinkedHashMap<>();
		_PREFIX_OPERATORS.put("-", new Operator(10, OperatorConstants.OPERATOR_NEG));
		_PREFIX_OPERATORS.put("~", new Operator(10, OperatorConstants.OPERATOR_BIT_NEG));
		_PREFIX_OPERATORS.put("!", new Operator(10, OperatorConstants.OPERATOR_NOT));
		_PREFIX_OPERATORS.put("+", new Operator(10, OperatorConstants.OPERATOR_NUMBER));
		_PREFIX_OPERATORS.put("!!", new Operator(10, OperatorConstants.OPERATOR_BOOLEAN));
		_PREFIX_OPERATORS.put("...", new Operator(10, OperatorType.SPECIAL_SYNTAX));
		_PREFIX_OPERATORS.put("$", new Operator(20, OperatorType.VARIABLE_DECLARATION));

		_INFIX_OPERATORS = new LinkedHashMap<>();
		_INFIX_OPERATORS.put("=", new Operator(0, OperatorType.ASSIGNMENT).withResultPrecedence(0));
		_INFIX_OPERATORS.put("?", new Operator(1, OperatorType.SPECIAL_SYNTAX));
		_INFIX_OPERATORS.put(":", new Operator(1, OperatorType.SPECIAL_SYNTAX).withResultPrecedence(1));
		_INFIX_OPERATORS.put("|>", new Operator(2, OperatorType.PIPELINE));
		_INFIX_OPERATORS.put("&&", new Operator(2, OperatorConstants.OPERATOR_AND));
		_INFIX_OPERATORS.put("||", new Operator(2, OperatorConstants.OPERATOR_OR));
		_INFIX_OPERATORS.put("??", new Operator(2, OperatorConstants.OPERATOR_COALESCE));
		_INFIX_OPERATORS.put("!!", new Operator(2, OperatorConstants.OPERATOR_ELSE));
		_INFIX_OPERATORS.put("<", new Operator(3, OperatorConstants.OPERATOR_LT));
		_INFIX_OPERATORS.put("<=", new Operator(3, OperatorConstants.OPERATOR_LTE));
		_INFIX_OPERATORS.put(">", new Operator(3, OperatorConstants.OPERATOR_GT));
		_INFIX_OPERATORS.put(">=", new Operator(3, OperatorConstants.OPERATOR_GTE));
		_INFIX_OPERATORS.put("==", new Operator(3, OperatorConstants.OPERATOR_EQ));
		_INFIX_OPERATORS.put("~", new Operator(3, OperatorConstants.OPERATOR_IS));
		_INFIX_OPERATORS.put("!=", new Operator(3, OperatorConstants.OPERATOR_NEQ));
		_INFIX_OPERATORS.put("^", new Operator(4, OperatorConstants.OPERATOR_BIT_XOR));
		_INFIX_OPERATORS.put("&", new Operator(4, OperatorConstants.OPERATOR_BIT_AND));
		_INFIX_OPERATORS.put("|", new Operator(4, OperatorConstants.OPERATOR_BIT_OR));
		_INFIX_OPERATORS.put("<<", new Operator(5, OperatorConstants.OPERATOR_BIT_SHL));
		_INFIX_OPERATORS.put(">>", new Operator(5, OperatorConstants.OPERATOR_BIT_SHR));
		_INFIX_OPERATORS.put(">>>", new Operator(5, OperatorConstants.OPERATOR_BIT_SHR_UNSIGNED));
		_INFIX_OPERATORS.put("+", new Operator(6, OperatorConstants.OPERATOR_ADD));
		_INFIX_OPERATORS.put("-", new Operator(6, OperatorConstants.OPERATOR_SUB));
		_INFIX_OPERATORS.put("*", new Operator(7, OperatorConstants.OPERATOR_MUL));
		_INFIX_OPERATORS.put("/", new Operator(7, OperatorConstants.OPERATOR_DIV));
		_INFIX_OPERATORS.put("%", new Operator(7, OperatorConstants.OPERATOR_MOD));
		_INFIX_OPERATORS.put("**", new Operator(8, OperatorConstants.OPERATOR_POW).withResultPrecedence(8));
		_INFIX_OPERATORS.put("->", new Operator(100, OperatorType.SPECIAL_SYNTAX));
		_INFIX_OPERATORS.put(".", new Operator(100, OperatorType.MEMBER_ACCESS));

		_OPERATOR_TOKENS = Stream.concat(_PREFIX_OPERATORS.entrySet().stream(), _INFIX_OPERATORS.entrySet().stream())
				.filter(v -> !v.getValue().isNameWord)
				.map(Map.Entry::getKey)
				.distinct()
				.sorted((a, b) -> b.length() - a.length())
				.collect(Collectors.toList());

		_WORD_OPERATOR_TOKENS = Stream.concat(_PREFIX_OPERATORS.entrySet().stream(), _INFIX_OPERATORS.entrySet().stream())
				.filter(v -> v.getValue().isNameWord)
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());

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
		this.createDiagnostic(message, this.getPosition());
	}

	public void createDiagnostic(String message, int index) {
		this.createDiagnostic(message, this.getPosition(index));
	}

	public void createDiagnostic(String message, Position position) {
		var diagnostic = new Diagnostic(message, position);
		this.diagnostics.add(diagnostic);
	}

	public void invalidToken() {
		if (this.diagnostics.isEmpty()) {
			this.createDiagnostic(_INVALID_TOKEN);
			return;
		}

		var lastDiagnostic = this.diagnostics.get(this.diagnostics.size() - 1);
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

			var hexString = this.input.substring(charStart, this.index - charStart).toString();
			try {
				int charValue = Integer.parseInt(hexString, 16);
				return String.valueOf((char) charValue);
			} catch (NumberFormatException ex) {
				this.createDiagnostic(_UNEXPECTED_EOF);
				return "\0";
			}
		}

		return switch (e) {
			case 'n' -> "\n";
			case 'r' -> "\r";
			case 't' -> "\t";
			case '\'' -> "'";
			case '\"' -> "\"";
			case '`' -> "`";
			case '\\' -> "\\";
			case '$' -> "$";
			default -> {
				this.createDiagnostic("Invalid escape sequence");
				yield "\\" + e;
			}
		};
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

	public Expression parseTemplate(String term) {
		var fragments = new ArrayList<Expression>();
		var resultArray = new Expression.ArrayLiteral(this.getPosition(), fragments);

		StringBuilder fragment = null;
		var fragmentStart = this.index;

		while (!this.isDone()) {
			var c = this.getCurrent();
			this.index++;

			if (String.valueOf(c).equals(term)) break;

			if (c == '\\') {
				if (fragment == null) {
					fragment = new StringBuilder();
					fragmentStart = this.index - 1;
				}

				fragment.append(this.parseEscapeSequence());
				continue;
			}

			if (c == '$' && !this.isDone() && this.getCurrent() == '{') {
				this.index++;
				var statements = this.parseBlock("}");
				var statementStart = this.index;

				if (statements.isEmpty()) continue;

				if (fragment != null) {
					fragments.add(new Expression.StringLiteral(this.getPosition(fragmentStart), fragment.toString()));
					fragment = null;
				}

				if (statements.size() == 1) {
					fragments.add(statements.get(0));
				} else {
					fragments.add(new Expression.Group(this.getPosition(statementStart), statements));
				}

				continue;
			}

			if (fragment == null) {
				fragment = new StringBuilder();
				fragmentStart = this.index - 1;
			}

			fragment.append(c);
		}

		if (fragment != null) {
			fragments.add(new Expression.StringLiteral(this.getPosition(fragmentStart), fragment.toString()));
			fragment = null;
		}

		return Expression.Invocation.makeMethodCall(resultArray.position(), resultArray, "join", List.of(new Expression.StringLiteral(resultArray.position(), "")));
	}

	public Token peekToken() {
		if (this._token == null) {
			return this.nextToken();
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

		this.parseEnumerated(terms, () -> {
			if (this.peekToken() == null) {
				return false;
			}

			Expression expression = this.consumeExpression();
			if (expression != null) {
				result.add(expression);
			}

			return true;
		});

		return result;
	}

	public Token nextToken() {
		this.skipWhitespace();
		var skippedNewline = this._skippedNewline;

		if (this.isDone()) {
			return this._token = null;
		}

		var start = this.index;

		if (this.consume("$\"")) {
			this._token = this.parseTemplate("\"");
			this._tokenStart = start;
			return this._token;
		}
		if (this.consume("$'")) {
			this._token = this.parseTemplate("'");
			this._tokenStart = start;
			return this._token;
		}
		if (this.consume("$`")) {
			this._token = this.parseTemplate("`");
			this._tokenStart = start;
			return this._token;
		}

		for (var token : _OPERATOR_TOKENS) {
			if (this.consume(token)) {
				if (token.equals("-") && (Character.isLetterOrDigit(this.getCurrent()) || this.getCurrent() == '_')) {
					this.index--;
					continue;
				}

				if (this.consume("=")) {
					this._token = OperatorInstance.makeAdvancedAssignment(this.getPosition(start), token);
					this._tokenStart = start;
					return this._token;
				}

				this._token = new OperatorInstance(this.getPosition(start), token);
				this._tokenStart = start;
				return this._token;
			}
		}

		if (this.consume("(")) {
			this._token = new Expression.Group(this.getPosition(start), this.parseBlock(")"));
			this._skippedNewline = skippedNewline;
			this._tokenStart = start;
			return this._token;
		}

		if (this.consume("[")) {
			this._token = new Expression.ArrayLiteral(this.getPosition(start), this.parseBlock("]"));
			this._skippedNewline = skippedNewline;
			this._tokenStart = start;
			return this._token;
		}

		if (this.consume("\"")) {
			this._token = this.parseString("\"");
			this._tokenStart = start;
			return this._token;
		}
		if (this.consume("'")) {
			this._token = this.parseString("'");
			this._tokenStart = start;
			return this._token;
		}
		if (this.consume("`")) {
			this._token = this.parseString("`");
			this._tokenStart = start;
			return this._token;
		}

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

					var parseResult = this.consumeExpression();
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

			this._token = map;
			this._tokenStart = start;
			return this._token;
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
				this._token = new Expression.NumberLiteral(this.getPosition(start), number);
				this._tokenStart = start;
				return this._token;
			} catch (NumberFormatException e) {
				this.createDiagnostic("Invalid number", start);
				this._token = null;
				this._tokenStart = start;
				return this._token;
			}
		}

		if (this.consume("\\")) {
			var parameters = new ArrayList<Parameter>();

			if (this.consume("(")) {
				var parameterExpressions = this.parseBlock(")");
				for (var expression : parameterExpressions) {
					var parameter = Parameter.parse(expression);

					if (parameter == null) {
						this.createDiagnostic("Invalid parameter expression", expression.position());
						continue;
					}

					parameters.add(parameter);
				}
			}

			this.skipWhitespace();

			Expression body;
			if (this.consume("{")) {
				int bodyStart = this.index;
				body = new Expression.Group(this.getPosition(bodyStart), this.parseBlock("}"));
			} else {
				this._token = null;
				body = this.consumeExpression();

				// Detect the use of placeholders and replace them with newly created parameters.
				// For example the function `\? + 1` will be converted to `\(_p_0) _p_0 + 1`.
				body = body.transform(new Expression.Transformer() {
					@Override
					public Expression apply(Expression expression) {
						if (expression instanceof Expression.Placeholder) {
							var index = parameters.size();
							var name = "_p_" + index;
							parameters.add(new Parameter(expression.position(), name));
							return new Expression.Identifier(expression.position(), name);
						}

						return expression;
					}

					@Override
					public boolean canApply(Expression expression) {
						// Prevent from visiting child function declarations. They would have
						// replaced their own placeholders anyway.
						return !(expression instanceof Expression.FunctionDeclaration);
					}
				});
			}

			this._token = new Expression.FunctionDeclaration(this.getPosition(start), parameters, body);
			this._tokenStart = start;
			return this._token;
		}

		var identifierName = this.consumeWord();
		if (identifierName.isEmpty()) return this._token = null;

		if (this.consume(":")) {
			var labelPosition = this.getPosition(start);
			this.skipWhitespace();
			this._token = null;

			Expression target = null;
			if (!this.isDone()) {
				target = this.consumeExpression();
				if (target == null) return null;
			}

			this._token = new Expression.Label(labelPosition, identifierName, target);
			this._tokenStart = start;
			return this._token;
		}

		if (_WORD_OPERATOR_TOKENS.contains(identifierName)) {
			if (this.consume("=")) {
				this._token = OperatorInstance.makeAdvancedAssignment(this.getPosition(start), identifierName);
				this._tokenStart = start;
				return this._token;
			}

			this._token = new OperatorInstance(this.getPosition(start), identifierName);
			this._tokenStart = start;
			return this._token;
		}

		this._token = new Expression.Identifier(this.getPosition(start), identifierName);
		this._tokenStart = start;
		return this._token;
	}

	public Expression consumeExpression() {
		var expression = this.parseExpression();
		if (expression == null) return null;

		if (this._token != null) {
			// The parsing has terminated on an incompatible token, rollback the parsing so it can be consumed next time
			this.index = this._tokenStart;
			this._token = null;
		}

		return expression;
	}

	public Expression parseExpression() {
		return this.parseExpression(0);
	}

	public Expression parseExpression(int precedence) {
		Token targetToken = this.peekToken();
		if (targetToken == null) {
			this.createDiagnostic(this.isDone() ? _UNEXPECTED_EOF : _INVALID_TOKEN);
			return null;
		}

		Expression target;
		if (targetToken instanceof OperatorInstance opInstance) {
			var prefixOperator = _PREFIX_OPERATORS.get(opInstance.token);
			if (prefixOperator == null) {
				if (opInstance.token.equals("?")) {
					// The token "?" can be used as an operator as part of the ternary condition
					// operator, but it can also be used as a placeholder token in function
					// declaration. To resolve this, we detect when "?" is **not** used as an infix
					// operator and create a Placeholder expression node.
					target = new Expression.Placeholder(targetToken.position());
					this.nextToken();
				} else {
					this.createDiagnostic("Unexpected operator", opInstance.position);
					this.nextToken();
					return null;
				}
			} else {
				this.nextToken();
				var operand = this.parseExpression(prefixOperator.resultPrecedence);
				if (operand == null) return null;

				if (prefixOperator.type == OperatorType.VARIABLE_DECLARATION) {
					target = new Expression.VariableDeclaration(opInstance.position, operand);
				} else if (prefixOperator.type == OperatorType.SPECIAL_SYNTAX) {
					if (opInstance.token.equals("...")) {
						target = new Expression.Spread(opInstance.position, operand);
					} else {
						throw new IllegalStateException("Cannot handle special syntax prefix operator of token '" + opInstance.token + "'");
					}

				} else {
					target = Expression.Invocation.makeMethodCall(opInstance.position, operand, prefixOperator.name, new ArrayList<>());
				}
			}
		} else {
			this.nextToken();
			target = (Expression) targetToken;
		}

		while (true) {
			var next = this.peekToken();
			if (next instanceof OperatorInstance nextOpInstance) {
				var infixOperator = _INFIX_OPERATORS.get(nextOpInstance.token);
				if (infixOperator == null) return target;

				// Advanced assignment operators should have precedence like the assignment operator, that is 0 to 0
				var operatorPrecedence = nextOpInstance.isAdvancedAssignment ? 0 : infixOperator.precedence;
				var operatorResultPrecedence = nextOpInstance.isAdvancedAssignment ? 0 : infixOperator.resultPrecedence;

				if (operatorPrecedence >= precedence) {
					if (infixOperator.type == OperatorType.SPECIAL_SYNTAX && nextOpInstance.token.equals(":")) {
						// The token ":" is only valid as a part of a conditional ternary operator, so don't try to parse it.
						return target;
					}
					this.nextToken();

					var operand = this.parseExpression(operatorResultPrecedence);
					if (operand == null) return target;

					if (nextOpInstance.isAdvancedAssignment) {
						if (infixOperator.type != OperatorType.INVOCATION) {
							this.createDiagnostic("Invalid use of operator in assignment", nextOpInstance.position);
							return null;
						}

						target = new Expression.AdvancedAssignment(nextOpInstance.position, infixOperator.name, target, operand);
						continue;
					}

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
					} else if (infixOperator.type == OperatorType.PIPELINE) {
						if (operand instanceof Expression.Invocation invocation) {
							var replacement = target;

							var transformer = new Expression.Transformer() {
								public boolean applied = false;

								@Override
								public Expression apply(Expression expression) {
									if (expression instanceof Expression.Placeholder) {
										this.applied = true;
										return replacement;
									}

									return expression;
								}

								@Override
								public boolean canApply(Expression expression) {
									return !this.applied && !(expression instanceof Expression.FunctionDeclaration);
								}
							};

							var invocationWithPlaceholderReplaced = invocation.transform(transformer);
							if (transformer.applied) {
								target = invocationWithPlaceholderReplaced;
							} else {
								// If the transformation was not applied
								target = invocation.withFirstArgument(target);
							}

							continue;
						}

						target = new Expression.Assignment(nextOpInstance.position, operand, target);
					} else if (infixOperator.type == OperatorType.SPECIAL_SYNTAX) {
						if (nextOpInstance.token.equals("?")) {
							var joinToken = this.peekToken();

							if (joinToken == null || !(joinToken instanceof OperatorInstance join && join.token.equals(":"))) {
								this.createDiagnostic("Expected ':' token for conditional operator", joinToken == null ? this.getPosition() : joinToken.position());
								return null;
							}

							this.nextToken();

							var alternative = this.parseExpression(operatorResultPrecedence);
							if (alternative == null) {
								this.createDiagnostic("Expected alternative expression for conditional operator");
								return null;
							}

							target = new Expression.Invocation(
									nextOpInstance.position,
									new Expression.Identifier(nextOpInstance.position, "@if"),
									List.of(target, operand, alternative));
						} else if (nextOpInstance.token.equals("->")) {
							if (this._skippedNewline) return target;

							var index = operand;

							if (index instanceof Expression.Identifier identifier) {
								index = new Expression.StringLiteral(identifier.position(), identifier.name());
							}

							target = Expression.Invocation.makeMethodCall(nextOpInstance.position(), target, OperatorConstants.OPERATOR_AT, List.of(index));
						} else if (nextOpInstance.token.equals(":")) {
							throw new IllegalStateException("Cannot handle special syntax infix operator of token '" + nextOpInstance.token + "'");
						}
					} else {
						throw new IllegalStateException("Invalid infix operator type: " + infixOperator.type);
					}
				} else {
					return target;
				}
			} else if (!this._skippedNewline && next instanceof Expression.Group group) {
				if (precedence > 100) return target;
				target = new Expression.Invocation(target.position(), target, group.children());
				this.nextToken();
			} else if (!this._skippedNewline && next instanceof Expression.ArrayLiteral arrayLiteral) {
				if (precedence > 100) return target;
				target = Expression.Invocation.makeMethodCall(arrayLiteral.position(), target, OperatorConstants.OPERATOR_AT, arrayLiteral.elements());
				this.nextToken();
			} else if (!this._skippedNewline && next instanceof Expression.StringLiteral stringLiteral) {
				if (precedence > 100) return target;

				if (target instanceof Expression.Invocation invocation) {
					target = invocation.withArgument(stringLiteral);
				} else {
					target = new Expression.Invocation(stringLiteral.position(), target, List.of(stringLiteral));
				}

				this.nextToken();
			} else {
				return target;
			}
		}
	}

	public Expression.Group parse() {
		return new Expression.Group(this.getPosition(), this.parseBlock());
	}

}
