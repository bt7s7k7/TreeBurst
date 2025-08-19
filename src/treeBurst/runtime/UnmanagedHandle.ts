import { LogMarker } from "../../prettyPrint/ObjectDescription"
import { ManagedObject } from "./ManagedObject"

export class UnmanagedHandle extends ManagedObject {
    public override[LogMarker.CUSTOM]() {
        return this
    }

    public override toString(): string {
        return this.name == null ? "<unmanaged>" : `<unmanaged ${this.name}>`
    }

    constructor(
        prototype: ManagedObject | null,
        public readonly value: any,
    ) { super(prototype) }
}
