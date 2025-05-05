import { isNumber } from "../comTypes/util"
import { Primitive, SKIP } from "./Primitive"
import { PrimitiveParser } from "./PrimitiveParser"

function _isHex(c: string) {
    if (isNumber(c)) return true
    const code = c.toLowerCase().charCodeAt(0)
    if (code >= "a".charCodeAt(0) && code <= "f".charCodeAt(0)) return true
    return false
}

export class NumberPrimitive extends Primitive<number> {
    public readonly allowNegatives
    public readonly allowFloat

    public parse(parser: PrimitiveParser): number | typeof SKIP {
        let isNegative = false

        if (this.allowNegatives && parser.matches("-") && isNumber(parser.input, parser.index + 1)) {
            parser.index++
            isNegative = true
        } else {
            // This primitive is not applicable
            if (!parser.matches(isNumber)) return SKIP
        }

        const type =
            parser.consume("0x") ? "hex" :
                parser.consume("0b") ? "bin" :
                    "dec"

        let src = ""
        let isFloat = false
        if (type == "dec") while (!parser.isDone() && parser.matches(isNumber)) { src += parser.getCurrent(); parser.index++ }
        if (type == "hex") while (!parser.isDone() && parser.matches(_isHex)) { src += parser.getCurrent(); parser.index++ }
        if (type == "bin") while (!parser.isDone() && parser.matches(["0", "1"])) { src += parser.getCurrent(); parser.index++ }

        if (this.allowFloat && type == "dec") {
            if (parser.matches(".") && !isNumber(parser.input, parser.index + 1)) {
                // Probably a member access, i.e. "58.toString", therefore stop parsing number
            } else {
                if (parser.consume(".")) {
                    src += "."
                    isFloat = true
                    while (!parser.isDone() && parser.matches(isNumber)) { src += parser.getCurrent(); parser.index++ }
                }

                if (parser.consume("e") || parser.consume("E")) {
                    src += "e"
                    isFloat = true
                    if (parser.consume("+")) src += "+"
                    else if (parser.consume("-")) src += "-"
                    while (!parser.isDone() && parser.matches(isNumber)) { src += parser.getCurrent(); parser.index++ }
                }
            }
        }

        let value = type == "dec" ? (isFloat ? parseFloat(src) : parseInt(src))
            : parseInt(src, type == "hex" ? 16 : 2)

        if (isNegative) {
            value = -value
        }

        return value
    }

    constructor(
        { allowNegatives = true, allowFloat = true } = {},
    ) {
        super()

        this.allowNegatives = allowNegatives
        this.allowFloat = allowFloat
    }
}

export const NUMBER_PRIMITIVE = /*#__PURE__*/ new NumberPrimitive()
export const INTEGER_PRIMITIVE = /*#__PURE__*/ new NumberPrimitive({ allowFloat: false })
export const POSITIVE_INTEGER_PRIMITIVE = /*#__PURE__*/ new NumberPrimitive({ allowFloat: false, allowNegatives: false })
