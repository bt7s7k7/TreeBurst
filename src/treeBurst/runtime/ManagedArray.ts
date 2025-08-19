import { cloneArray } from "../../comTypes/util"
import { LogMarker } from "../../prettyPrint/ObjectDescription"
import { Diagnostic } from "../support/Diagnostic"
import { LABEL_EXCEPTION } from "./evaluateExpression"
import { ExpressionResult } from "./ExpressionResult"
import { INTRINSIC } from "./GlobalScope"
import { ManagedObject } from "./ManagedObject"
import { ManagedValue } from "./ManagedValue"

export class ManagedArray extends ManagedObject {
    public [LogMarker.CUSTOM](): any {
        const result = cloneArray(this.elements)
        Object.defineProperty(result, "constructor", {
            value: { [LogMarker.CUSTOM_NAME]: this.toString() },
            enumerable: false,
        })
        return result
    }

    public normalizeIndex(index: number, result: ExpressionResult) {
        if (index < 0) index = this.elements.length + index

        if (index < 0 || index >= this.elements.length) {
            result.value = new Diagnostic(`Index ${index} out of range of array of length ${this.elements.length}`, INTRINSIC)
            result.label = LABEL_EXCEPTION
            return 0
        }

        return index
    }

    public override getProperty(name: string, result: ExpressionResult): boolean {
        if (name == "length") { result.value = this.elements.length; return true }
        return super.getProperty(name, result)
    }

    constructor(
        prototype: ManagedObject | null,
        public readonly elements: ManagedValue[],
    ) { super(prototype) }

    public static readonly [LogMarker.CUSTOM_NAME] = "Array"
}
