import { VOID } from "./GlobalScope"
import { ManagedValue } from "./ManagedValue"

export class ExpressionResult {
    public value: ManagedValue = VOID
    public label: string | null = null
}
