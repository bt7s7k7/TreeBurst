import { GenericParser } from "../../comTypes/GenericParser"
import { Readwrite } from "../../comTypes/types"
import { isNumber, isWord, joinIterable, unreachable } from "../../comTypes/util"
import { OPERATOR_ADD, OPERATOR_AND, OPERATOR_BOOLEAN, OPERATOR_DIV, OPERATOR_EQ, OPERATOR_GT, OPERATOR_GTE, OPERATOR_IS, OPERATOR_LT, OPERATOR_LTE, OPERATOR_MOD, OPERATOR_MUL, OPERATOR_NEG, OPERATOR_NEQ, OPERATOR_NOT, OPERATOR_NUMBER, OPERATOR_OR, OPERATOR_POW, OPERATOR_SUB } from "../const"
import { Diagnostic } from "../support/Diagnostic"
import { InputDocument } from "../support/InputDocument"
import { Position } from "../support/Position"
import { Expression } from "./Expression"

export const OPERATOR_INVOCATION = Symbol.for("treeBurst.operator.invocation")
export const OPERATOR_ASSIGNMENT = Symbol.for("treeBurst.operator.assignment")
export const OPERATOR_MEMBER_ACCESS = Symbol.for("treeBurst.operator.memberAccess")
export const OPERATOR_VARIABLE_DECLARATION = Symbol.for("treeBurst.operator.memberAccess")
export type OperatorType = typeof OPERATOR_INVOCATION | typeof OPERATOR_ASSIGNMENT | typeof OPERATOR_MEMBER_ACCESS | typeof OPERATOR_VARIABLE_DECLARATION

export class Operator {
    public readonly precedence: number
    public readonly type: OperatorType
    public readonly name: string | null
    public readonly resultPrecedence: number

    public withResultPresentence(value: number) {
        (this as Readwrite<this>).resultPrecedence = value
        return this
    }

    constructor(precedence: number, name: string)
    constructor(precedence: number, type: OperatorType)
    constructor(precedence: number, target: OperatorType | string) {
        this.precedence = precedence

        if (typeof target === "string") {
            this.type = OPERATOR_INVOCATION
            this.name = target
        } else {
            this.type = target
            this.name = null
        }

        // Initialize the ResultPrecedence property
        this.resultPrecedence = this.precedence + 1
    }
}

const _PREFIX_OPERATORS = new Map<string, Operator>([
    ["-", new Operator(10, OPERATOR_NEG)],
    ["!", new Operator(10, OPERATOR_NOT)],
    ["+", new Operator(10, OPERATOR_NUMBER)],
    ["!!", new Operator(10, OPERATOR_BOOLEAN)],

    ["$", new Operator(20, OPERATOR_VARIABLE_DECLARATION)],
])

const _INFIX_OPERATORS = new Map<string, Operator>([
    ["=", new Operator(0, OPERATOR_ASSIGNMENT).withResultPresentence(0)],

    ["&&", new Operator(1, "@" + OPERATOR_AND)],
    ["||", new Operator(1, "@" + OPERATOR_OR)],

    ["<", new Operator(2, OPERATOR_LT)],
    ["<=", new Operator(2, OPERATOR_LTE)],
    [">", new Operator(2, OPERATOR_GT)],
    [">=", new Operator(2, OPERATOR_GTE)],
    ["==", new Operator(2, OPERATOR_EQ)],
    ["is", new Operator(2, OPERATOR_IS)],
    ["!=", new Operator(2, OPERATOR_NEQ)],

    ["+", new Operator(3, OPERATOR_ADD)],
    ["-", new Operator(3, OPERATOR_SUB)],

    ["*", new Operator(4, OPERATOR_MUL)],
    ["/", new Operator(4, OPERATOR_DIV)],
    ["%", new Operator(4, OPERATOR_MOD)],

    ["**", new Operator(5, OPERATOR_POW).withResultPresentence(5)],

    [".", new Operator(100, OPERATOR_MEMBER_ACCESS)],
])
const _OPERATOR_TOKENS: string[] = [...new Set(joinIterable(_PREFIX_OPERATORS.keys(), _INFIX_OPERATORS.keys()))]
_OPERATOR_TOKENS.sort((a, b) => b.length - a.length)

export class OperatorInstance {
    constructor(
        public readonly position: Position,
        public readonly token: string,
    ) { }
}

const _UNEXPECTED_EOF = "Unexpected end of input"
const _INVALID_TOKEN = "Invalid token"

export type Token = Expression | OperatorInstance

export class TreeBurstParser extends GenericParser {
    public diagnostics: Diagnostic[] = []

    protected _skippedNewline = false
    protected _lastSkippedIndex = -1
    protected _token: Token | null = null

    public getPosition(index: number | null = null) {
        if (index == null) {
            return new Position(this.document, this.index, 1)
        } else {
            return new Position(this.document, index, this.index - index)
        }
    }

    public createDiagnostic(message: string, position: Position): void
    public createDiagnostic(message: string, index: number): void
    public createDiagnostic(message: string): void
    public createDiagnostic(message: string, target?: Position | number) {
        const diagnostic = new Diagnostic(message,
            target == undefined ? (
                this.getPosition()
            ) : typeof target == "number" ? (
                this.getPosition(target)
            ) : (
                target
            ),
        )
        this.diagnostics.push(diagnostic)
    }

    public invalidToken() {
        const lastDiagnostic = this.diagnostics.at(-1)
        if (lastDiagnostic == undefined) {
            this.createDiagnostic(_INVALID_TOKEN)
            return
        }

        if (lastDiagnostic.message == _INVALID_TOKEN && lastDiagnostic.position.index + lastDiagnostic.position.length == this.index) {
            lastDiagnostic.position.setLength(lastDiagnostic.position.length + 1)
            return
        }
    }

    public consumeWord() {
        const start = this.index
        let prefix = ""
        if (this.consume("@")) {
            prefix = "@"
        }

        const word = this.readWhile((v, i) => isWord(v, i) || v[i] == ":")
        if (word.length == 0) {
            this.index = start
            return ""
        }

        return prefix + word
    }

    public skipWhitespace(): void {
        if (this.index === this._lastSkippedIndex) {
            return
        }

        let currSkippedNewLine = false
        while (!this.isDone()) {
            this.skipWhile(() => this.getCurrent() === " " || this.getCurrent() === "\t")
            if (this.isDone()) {
                break
            }

            const currentChar = this.getCurrent()
            if (currentChar === "\n" || currentChar === "\r") {
                currSkippedNewLine = true
                this.index++
                continue
            }

            if (this.consume("//")) {
                this.skipWhile(() => this.getCurrent() !== "\n")
                continue
            }

            if (this.consume("/*")) {
                let depth = 1

                while (!this.isDone() && depth > 0) {
                    if (this.consume("/*")) {
                        depth++
                        continue
                    }

                    if (this.consume("*/")) {
                        depth--
                        continue
                    }

                    this.index++
                }
                continue
            }

            break
        }

        this._lastSkippedIndex = this.index
        this._skippedNewline = currSkippedNewLine
    }

    public parseEscapeSequence() {
        if (this.index >= this.input.length) {
            this.createDiagnostic(_UNEXPECTED_EOF)
            return ""
        }

        const e = this.input[this.index]
        this.index++

        if (e === "x") {
            const charStart = this.index
            this.index += 2 // Move past the two hex characters

            if (this.index > this.input.length) {
                this.createDiagnostic(_UNEXPECTED_EOF)
                return ""
            }

            const hexString = this.input.substring(charStart, this.index)
            const charValue = parseInt(hexString, 16)

            if (isNaN(charValue) || hexString.length !== 2) {
                this.createDiagnostic(_UNEXPECTED_EOF)
                return "\0"
            }

            return String.fromCharCode(charValue)
        }

        switch (e) {
            case "n":
                return "\n"
            case "r":
                return "\r"
            case "t":
                return "\t"
            case "'":
                return "'"
            case "\"":
                return "\""
            case "`":
                return "`"
            case "\\":
                return "\\"
            case "$":
                return "$"
            default:
                throw new Error("Invalid escape character")
        }
    }

    public parseString(term: string) {
        let value: string = ""
        const start = this.index - 1

        while (!this.isDone()) {
            const c = this.getCurrent()
            this.index++

            if (c === term) break
            if (c === "\\") {
                value += this.parseEscapeSequence()
                continue
            }

            value += c
        }

        return new Expression.StringLiteral(this.getPosition(start), value)
    }


    public peekToken() {
        if (this._token == null) {
            return this.nextToken()
        }

        return this._token
    }

    public parseBlock(...terms: string[]) {
        this._token = null
        const result: Expression[] = []

        top: while (true) {
            if (this.peekToken() == null) {
                this.skipWhitespace()
                if (this.isDone()) break

                if (this.consume(",")) continue

                if (terms.length > 0) {
                    for (const term of terms) {
                        if (this.consume(term)) break top
                    }
                }

                this.invalidToken()
                this.index++
                continue
            }

            const expression = this.parseExpression()
            if (expression == null) continue
            result.push(expression)
        }

        return result
    }


    public nextToken(): Token | null {
        this.skipWhitespace()

        const skippedNewline = this._skippedNewline

        if (this.isDone()) {
            return this._token = null
        }

        const start = this.index
        for (const token of _OPERATOR_TOKENS) {
            if (this.consume(token)) {
                return this._token = new OperatorInstance(this.getPosition(start), token)
            }
        }

        if (this.consume("(")) {
            this._token = new Expression.Group(this.getPosition(start), this.parseBlock(")"))
            this._skippedNewline = skippedNewline
            return this._token
        }

        if (this.consume("[")) {
            this._token = new Expression.ArrayLiteral(this.getPosition(start), this.parseBlock("]"))
            this._skippedNewline = skippedNewline
            return this._token
        }

        if (isNumber(this.input, this.index) || this.getCurrent() == "-") {
            let numberText = ""
            if (this.consume("-")) numberText += "-"

            numberText += this.readWhile(isNumber)

            if (this.consume(".")) {
                numberText += "."
                numberText += this.readWhile(isNumber)
            }

            const number = parseFloat(numberText)

            if (isNaN(number)) {
                this.createDiagnostic("Invalid number", start)
            }

            return this._token = new Expression.NumberLiteral(this.getPosition(start), number)
        }

        if (this.consume("\"")) return this._token = this.parseString("\"")
        if (this.consume("'")) return this._token = this.parseString("'")
        if (this.consume("`")) return this._token = this.parseString("`")

        if (this.consume("\\")) {
            const parameters: string[] = []

            if (this.consume("(")) {
                while (!this.isDone()) {
                    this.skipWhitespace()
                    if (this.consume(")")) break
                    if (this.consume(",")) continue
                    let parameter = this.consumeWord()
                    if (parameter.length == 0) {
                        this.createDiagnostic("Expected parameter")
                    } else {
                        parameters.push(parameter)
                    }
                }
            }

            this.skipWhitespace()

            let body: Expression
            if (this.consume("{")) {
                const bodyStart = this.index
                body = new Expression.Group(this.getPosition(bodyStart), this.parseBlock("}"))
            }
            else {
                this._token = null
                const expression = this.parseExpression()
                if (expression == null) return null
                body = expression
            }

            return this._token = new Expression.FunctionDeclaration(this.getPosition(start), parameters, body)
        }

        let variable = this.consumeWord()
        if (variable.length == 0) return this._token = null
        return this._token = new Expression.Identifier(this.getPosition(start), variable)
    }

    public ensureTokenIsOperator() {
        const token = this.peekToken()

        if (token instanceof OperatorInstance) return true
        if (token instanceof Expression.Group || token instanceof Expression.ArrayLiteral) return true

        return false
    }

    public parseExpression(precedence = 0): Expression | null {
        let target = this.peekToken()
        if (target == null) {
            this.createDiagnostic(this.isDone() ? _UNEXPECTED_EOF : _INVALID_TOKEN)
            return null
        }

        if (target instanceof OperatorInstance) {
            const prefixOperator = _PREFIX_OPERATORS.get(target.token)
            if (prefixOperator == null) {
                this.createDiagnostic("Unexpected operator", target.position)
                this.nextToken()
                return null
            } else {
                this.nextToken()
                const operand = this.parseExpression(prefixOperator.resultPrecedence)
                if (operand == null) return null
                if (!(operand instanceof Expression)) unreachable()

                if (prefixOperator.type == OPERATOR_VARIABLE_DECLARATION) {
                    target = new Expression.VariableDeclaration(target.position, operand)
                } else {
                    target = Expression.Invocation.makeMethodCall(target.position, operand, prefixOperator.name!, [])
                }
            }
        }
        else {
            this.nextToken()
        }

        while (true) {
            if (!this.ensureTokenIsOperator()) return target
            const next = this.peekToken()

            if (next instanceof OperatorInstance) {
                const infixOperator = _INFIX_OPERATORS.get(next.token)
                if (infixOperator == null) {
                    this.createDiagnostic("Unexpected operator", target.position)
                    this.nextToken()
                    return target
                }

                if (infixOperator.precedence >= precedence) {
                    this.nextToken()
                    const operand = this.parseExpression(infixOperator.resultPrecedence)
                    if (operand == null) return target
                    if (!(operand instanceof Expression)) unreachable()

                    if (infixOperator.type == OPERATOR_INVOCATION) {
                        target = Expression.Invocation.makeMethodCall(next.position, target, infixOperator.name!, [operand])
                    } else if (infixOperator.type == OPERATOR_MEMBER_ACCESS) {
                        if (operand instanceof Expression.Identifier) {
                            target = new Expression.MemberAccess(operand.position, target, operand.name)
                        } else {
                            this.createDiagnostic("Expected member name")
                            return target
                        }
                    } else if (infixOperator.type == OPERATOR_ASSIGNMENT) {
                        if (target instanceof Expression.Invocation) {
                            target.args.push(operand)
                            continue
                        }

                        target = new Expression.Assignment(next.position, target, operand)
                    }
                } else {
                    return target
                }
            } else if (!this._skippedNewline && next instanceof Expression.Group) {
                if (precedence > 100) return target
                target = new Expression.Invocation(target.position, target, next.children)
                this.nextToken()
            } else if (!this._skippedNewline && next instanceof Expression.ArrayLiteral) {
                if (precedence > 100) return target

                target = Expression.Invocation.makeMethodCall(target.position, target, "k:at", next.elements)
                this.nextToken()
            } else {
                return target
            }
        }
    }

    public parse() {
        return new Expression.Group(this.getPosition(), this.parseBlock())
    }

    constructor(
        public readonly document: InputDocument,
    ) { super(document.content) }
}
