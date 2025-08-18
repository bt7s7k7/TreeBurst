import { ManagedObject } from "./ManagedObject"

export class UnmanagedHandle extends ManagedObject {
    constructor(
        prototype: ManagedObject | null,
        public readonly value: any,
    ) { super(prototype) }
}
