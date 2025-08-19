import { Diagnostic } from "../support/Diagnostic"
import { LABEL_EXCEPTION } from "./evaluateExpression"
import { ExpressionResult } from "./ExpressionResult"
import { GlobalScope, INTRINSIC, verifyArguments } from "./GlobalScope"
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

    public static simple(scope: GlobalScope, parameters: string[], handler: NativeHandler) {
        return new NativeFunction(scope.globalScope.FunctionPrototype, parameters, function (args, scope, result) {
            if (!verifyArguments(args, parameters, result)) return
            if (args.length > parameters.length) {
                result.value = new Diagnostic(`Too many arguments, expected ${parameters.length}, but got ${args.length}`, INTRINSIC)
                result.label = LABEL_EXCEPTION
                return
            }

            handler.call(this, args, scope, result)
            return
        })
    }
}
