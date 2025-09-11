import { FunctionOverload } from "./FunctionOverload"
import { SymbolDatabase } from "./SymbolDatabase"


export class SymbolHandle {
    public value: SymbolHandle | null = null
    public prototype: SymbolHandle | null = null
    public isEntry = false
    public isFunction = false
    public isVariadicFunction = false
    public overloads: FunctionOverload[] | null = null
    public isPendingExplicitParameters = true
    public templates: SymbolHandle[] | null = null

    public readonly children: SymbolHandle[] = []
    public readonly sites: string[] = []
    public readonly summary: string[] = []

    public apply(template: SymbolHandle) {
        this.isFunction ||= template.isFunction
        this.isVariadicFunction ||= template.isVariadicFunction

        if (template.overloads) {
            (this.overloads ??= []).push(...template.overloads)
        }

        if (template.value) {
            this.value ??= template.value
        }

        this.summary.push(...template.summary)
    }

    public getChild(name: string) {
        return this.db.getSymbol(this.name + "." + name)
    }

    constructor(
        public readonly db: SymbolDatabase,
        public readonly name: string,
    ) { }
}
