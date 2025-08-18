import { ExpressionResult } from "./ExpressionResult"
import { ManagedFunction } from "./ManagedFunction"
import { ManagedObject } from "./ManagedObject"
import { ManagedValue } from "./ManagedValue"
import { Scope } from "./Scope"

export type NativeHandler = (this: NativeFunction, args: ManagedValue[], scope: Scope, result: ExpressionResult) => void

export class NativeFunction extends ManagedFunction {
    public invoke(args: ManagedValue[], scope: Scope, result: ExpressionResult): void {
        this.handler(args, scope, result)
    }

    constructor(
        prototype: ManagedObject | null,
        parameters: string[],
        public readonly handler: NativeHandler,
    ) { super(prototype, parameters) }
}
