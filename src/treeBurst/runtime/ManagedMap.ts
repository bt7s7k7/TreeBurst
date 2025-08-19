import { LogMarker } from "../../prettyPrint/ObjectDescription"
import { ExpressionResult } from "./ExpressionResult"
import { ManagedObject } from "./ManagedObject"
import { ManagedValue } from "./ManagedValue"

export class ManagedMap extends ManagedObject {
    public readonly entries = new Map<ManagedValue, ManagedValue>()

    public [LogMarker.CUSTOM](): any {
        const result = new Map(this.entries)
        Object.defineProperty(result, "constructor", {
            value: { [LogMarker.CUSTOM_NAME]: this.toString() },
            enumerable: false,
        })
        return result
    }

    public override getProperty(name: string, result: ExpressionResult): boolean {
        if (name == "length") { result.value = this.entries.size; return true }
        return super.getProperty(name, result)
    }

    constructor(
        prototype: ManagedObject | null,
    ) { super(prototype) }

    public static readonly [LogMarker.CUSTOM_NAME] = "Map"
}
