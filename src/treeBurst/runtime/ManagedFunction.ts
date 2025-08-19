import { ExpressionResult } from "./ExpressionResult"
import { ManagedObject } from "./ManagedObject"
import { ManagedValue } from "./ManagedValue"
import { Scope } from "./Scope"

export abstract class ManagedFunction extends ManagedObject {
    public abstract invoke(args: ManagedValue[], scope: Scope, result: ExpressionResult): void

    public override getProperty(name: string, result: ExpressionResult): boolean {
        if (name == "name") { result.value = this.name; return true }
        return super.getProperty(name, result)
    }

    public override toString() {
        return `[function ${this.name == null ? "<anon>" : this.name}]`
    }

    constructor(
        prototype: ManagedObject | null,
        public readonly parameters: string[],
    ) { super(prototype) }
}
