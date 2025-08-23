import { GlobalScope, VOID } from "./GlobalScope"
import { ManagedValue } from "./ManagedValue"

export class Variable {
    public value: ManagedValue = VOID
}

export class Scope {
    public readonly variables = new Map<string, Variable>()

    public findVariable(name: string): Variable | null {
        return this.variables.get(name) ?? this.parent?.findVariable(name) ?? null
    }

    public declareVariable(name: string): Variable | null {
        if (this.variables.has(name)) {
            return null
        }

        const variable = new Variable()
        this.variables.set(name, variable)
        return variable
    }

    public makeChild() { return new Scope(this, this.globalScope) }

    constructor(
        public readonly parent: Scope | null,
        public readonly globalScope: GlobalScope,
    ) { }
}

