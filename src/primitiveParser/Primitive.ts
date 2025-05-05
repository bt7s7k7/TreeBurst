import { PrimitiveParser } from "./PrimitiveParser"

export const SKIP = Symbol.for("primitiveParser.skip")

export abstract class Primitive<T> {
    public abstract parse(parser: PrimitiveParser): T | typeof SKIP

    public static create<T>(callback: (parser: PrimitiveParser) => (T | typeof SKIP)): Primitive<T> {
        return new class extends Primitive<T> {
            public parse(parser: PrimitiveParser): typeof SKIP | T {
                return this._callback(parser)
            }

            constructor(
                protected _callback: (parser: PrimitiveParser) => (T | typeof SKIP),
            ) { super() }
        }(callback)
    }
}
