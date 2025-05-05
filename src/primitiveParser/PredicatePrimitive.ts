import { isWord } from "../comTypes/util"
import { Primitive, SKIP } from "./Primitive"
import { PrimitiveParser } from "./PrimitiveParser"

export class PredicatePrimitive extends Primitive<string> {
    public parse(parser: PrimitiveParser): string | typeof SKIP {
        const value = parser.readWhile(this.predicate)
        if (value) {
            return value
        }

        return SKIP
    }

    constructor(
        public readonly predicate: (v: string, i: number) => boolean,
    ) {
        super()
    }
}

export const WORD_PRIMITIVE = new PredicatePrimitive(isWord)
