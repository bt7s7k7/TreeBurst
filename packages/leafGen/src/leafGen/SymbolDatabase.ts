import { FunctionOverload } from "./FunctionOverload"
import { printWarn } from "./print"
import { SymbolHandle } from "./SymbolHandle"

const _MANAGED_SYMBOL_BY_CLASS = new Map([
    ["Primitive.Number", "Number"],
    ["Primitive.Boolean", "Boolean"],
    ["Primitive.String", "String"],
    ["ManagedTable", "Table"],
    ["ManagedArray", "Array"],
    ["ManagedMap", "Map"],
    ["ManagedFunction", "Function"],
    ["ManagedValue", "any"],
])

export class SymbolDatabase {
    protected readonly _symbols = new Map<string, SymbolHandle>()
    protected readonly _nativeAliases = new Map<string, SymbolHandle>()

    public readonly globalScope = new class extends SymbolHandle {
        public override getChild(name: string) {
            return this.db.getSymbol(name)
        }
    }(this, "<global>")

    public readonly unknown = this.getSymbol("??")

    public getSymbol(name: string) {
        const existing = this._symbols.get(name)
        if (existing) return existing

        const dot = name.lastIndexOf(".")
        let parent = null

        if (dot != -1) {
            parent = this.getSymbol(name.slice(0, dot))
        }

        const symbol = new SymbolHandle(this, name)
        this._symbols.set(name, symbol)
        parent?.children.push(symbol)

        return symbol
    }

    public tryGetSymbol(name: string) {
        return this._symbols.get(name) ?? null
    }

    public getSymbolNames() {
        return this._symbols.keys()
    }

    public findManagedSymbolByNativeClass(className: string) {
        const preset = _MANAGED_SYMBOL_BY_CLASS.get(className)
        if (preset) return this.getSymbol(preset)

        return this.getSymbol(className)
    }

    public addNativeAlias(className: string, symbol: SymbolHandle) {
        const existing = this._nativeAliases.get(className)
        if (existing) {
            printWarn(`Duplicate native alias, conflict between "${existing.name}" and "${symbol.name}"`)
            return
        }

        this._nativeAliases.set(className, symbol)
    }

    public postProcessSymbols() {
        for (const [name, symbol] of this._nativeAliases) {
            this._symbols.set(name, symbol)
        }

        for (const symbol of this._symbols.values()) {
            if (symbol.prototype == null && symbol.isFunction) {
                symbol.prototype = this.getSymbol("Function").getChild("prototype")
            }

            if (symbol.isFunction && symbol.overloads == null) {
                if (symbol.isVariadicFunction) {
                    symbol.overloads = [new FunctionOverload([], null).withVariadic()]
                } else {
                    symbol.overloads = [new FunctionOverload([], null)]
                }
            }

            if (symbol.isFunction) {
                for (const overload of symbol.overloads!) {
                    if (overload.types == null) continue
                    for (let i = 0; i < overload.types.length; i++) {
                        const type = overload.types[i]
                        const aliased = this._nativeAliases.get(type.name)
                        if (aliased) {
                            overload.types[i] = aliased
                        }
                    }
                }
            }

            if (symbol.templates) {
                for (const template of symbol.templates) {
                    symbol.apply(template)
                }
            }
        }
    }
}
