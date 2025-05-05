import { Primitive, SKIP } from "./Primitive"
import { PrimitiveParser } from "./PrimitiveParser"

export class StringPrimitive extends Primitive<string> {
    protected readonly _escapePredicate = (v: string, i: number) => v[i] == "\\" || v[i] == this.terminator

    public parse(parser: PrimitiveParser): string | typeof SKIP {
        if (!parser.consume(this.terminator)) return SKIP
        const value: string[] = []

        while (!parser.isDone()) {
            const fragment = parser.readUntil(this._escapePredicate)
            if (fragment) value.push(fragment)
            if (parser.consume(this.terminator)) break

            if (parser.consume("\\")) {
                const escaped = parser.getCurrent()
                parser.index++

                if (escaped == this.terminator) value.push(escaped)
                else if (escaped == "n") value.push("\n")
                else if (escaped == "t") value.push("\t")
                else if (escaped == "b") value.push("\b")
                else if (escaped == "r") value.push("\r")
                else if (escaped == "0") value.push("\0")
                else if (escaped == "\\") value.push("\\")
                else if (escaped == "x") {
                    parser.tokenStart = parser.index
                    const hex = parseInt(parser.read(2), 16)
                    if (isNaN(hex)) {
                        parser.createTokenDiagnostic("Invalid hex code")
                    } else {
                        value.push(String.fromCharCode(hex))
                    }
                } else if (escaped == "u") {
                    parser.tokenStart = parser.index
                    const hex = parseInt(parser.read(4), 16)
                    if (isNaN(hex)) {
                        parser.createTokenDiagnostic("Invalid hex code")
                    } else {
                        value.push(String.fromCharCode(hex))
                    }
                } else {
                    parser.index--
                    parser.unexpectedToken()
                    value.push("\\")
                }
            }
        }

        return value.join("")
    }

    constructor(
        public readonly terminator: string,
    ) { super() }
}

export const STRING_PRIMITIVE = new StringPrimitive("\"")
export const SINGLE_STRING_PRIMITIVE = new StringPrimitive("'")
