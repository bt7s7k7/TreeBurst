import { GenericParser } from "../comTypes/GenericParser"
import { isWhitespace } from "../comTypes/util"
import { Diagnostic } from "./Diagnostic"
import { InputDocument } from "./InputDocument"
import { ParserState } from "./ParserState"
import { Position } from "./Position"
import { Primitive, SKIP } from "./Primitive"

export class PrimitiveParser extends GenericParser {
    public readonly diagnostics: Diagnostic[] = []
    protected readonly _states: Map<ParserState<any>, any>[] = [new Map()]
    protected get _state() { return this._states.at(-1)! }
    public tokenStart = 0

    public addDiagnostic(diagnostic: Diagnostic) {
        this.diagnostics.push(diagnostic)
    }

    public getPosition() {
        return new Position(this.document, this.index, 1)
    }

    public getTokenPosition() {
        return new Position(this.document, this.tokenStart, this.index - this.tokenStart)
    }

    public createDiagnostic(message: string) {
        this.addDiagnostic(new Diagnostic(message, this.getPosition()))
    }

    public createTokenDiagnostic(message: string) {
        this.addDiagnostic(new Diagnostic(message, this.getTokenPosition()))
    }

    public parsePrimitive<T>(primitive: Primitive<T>) {
        return primitive.parse(this)
    }

    public setState<T>(state: ParserState<T>, value: T) {
        this._state.set(state, value)
    }

    public getState<T>(state: ParserState<T>): T {
        return this._state.get(state) ?? state.defaultValue
    }

    public pushState() {
        this._states.push(new Map(this._state))
    }

    public popState() {
        if (this._states.length == 1) {
            throw new Error("Cannot pop state, already at root")
        }

        this._states.pop()
    }

    public unexpectedToken() {
        if (this.diagnostics.length > 0) {
            const last = this.diagnostics.at(-1)!
            if (last.message == "Unexpected token" && last.position.end == this.index) {
                last.position.setLength(last.position.length + 1)
                return
            }
        }

        this.createDiagnostic("Unexpected token")
    }

    public skipWhitespace() {
        while (!this.isDone()) {
            this.skipWhile(isWhitespace)

            if (this.isDone()) break

            if (this.consume("/*")) {
                let depth = 1
                while (depth > 0 && !this.isDone()) {
                    if (this.consume("/*")) {
                        depth++
                    } else if (this.consume("*/")) {
                        depth--
                    } else {
                        this.index++
                    }
                }
                continue
            }

            if (this.consume("//")) {
                this.readUntil("\n")
                continue
            }

            break
        }
    }

    public parsePrimitives<T extends Primitive<any>[]>(primitives: T): PrimitiveParser._PrimitiveResults<T> | typeof SKIP {
        const start = this.index
        for (const primitive of primitives) {
            const result = primitive.parse(this)

            if (result == SKIP) {
                this.index = start
                continue
            }

            return result
        }

        return SKIP
    }

    public *parsePrimitivesUntil<T extends Primitive<any>[]>(predicate: ((v: string, i: number) => boolean) | string | null, primitives: T) {
        while (!this.isDone()) {
            this.skipWhitespace()

            if (this.isDone()) break

            this.tokenStart = this.index
            if (typeof predicate == "string") {
                if (this.consume(predicate)) break
            } else if (typeof predicate == "function") {
                if (this.matches(predicate)) break
            }

            const startIndex = this.index
            const result = this.parsePrimitives(primitives)
            if (result == SKIP) {
                this.unexpectedToken()
                this.index++
                continue
            }

            this.tokenStart = startIndex
            yield result as Exclude<PrimitiveParser._PrimitiveResults<T>, typeof SKIP>
        }
    }

    constructor(
        public readonly document: InputDocument,
    ) {
        super(document.content)
    }
}

export namespace PrimitiveParser {
    export type _PrimitiveResults<T extends Primitive<any>[]> = { [P in keyof T]: ReturnType<T[P]["parse"]> }[number]
}
