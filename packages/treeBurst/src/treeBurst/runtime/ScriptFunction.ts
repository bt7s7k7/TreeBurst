import { Expression } from "../syntax/Expression"
import { evaluateExpression, LABEL_RETURN } from "./evaluateExpression"
import { ExpressionResult } from "./ExpressionResult"
import { VOID } from "./GlobalScope"
import { ManagedFunction } from "./ManagedFunction"
import { ManagedObject } from "./ManagedObject"
import { ManagedValue } from "./ManagedValue"
import { Scope } from "./Scope"

export class ScriptFunction extends ManagedFunction {
    public override invoke(args: ManagedValue[], scope: Scope, result: ExpressionResult): void {
        const functionScope = scope.makeChild()

        for (let i = 0; i < this.parameters.length; i++) {
            const parameter = this.parameters[i]
            const arg = i < args.length ? args[i] : VOID

            functionScope.declareVariable(parameter)!.value = arg
        }

        evaluateExpression(this.body, functionScope, result)
        if (result.label == LABEL_RETURN) {
            result.label = null
        }

        return
    }

    constructor(
        prototype: ManagedObject | null,
        parameters: string[],
        public readonly body: Expression,
        public readonly scope: Scope,
    ) { super(prototype, parameters) }
}
