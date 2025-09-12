import { EMPTY_ARRAY } from "../comTypes/const"
import { unreachable } from "../comTypes/util"
import { OPERATOR_CONSTANTS } from "./constants"
import { FunctionOverload } from "./FunctionOverload"
import { printWarn } from "./print"
import { Project } from "./Project"
import { SymbolDatabase } from "./SymbolDatabase"
import { SymbolHandle } from "./SymbolHandle"

export class ScopeInfo {
    public isNativeHandleWrapper = false

    public setNativeHandleWrapper() {
        this.isNativeHandleWrapper = true
        return this
    }

    constructor(
        public readonly symbol: SymbolHandle,
        public readonly indent: number,
    ) { }
}

export class FileParser {
    public line = 0
    public isGlobalScope = false
    public readonly scopes: ScopeInfo[] = [new ScopeInfo(this.db.unknown, 0)]

    public getScope() {
        return this.scopes.at(-1) ?? unreachable()
    }

    public truncateScopesToIndent(indent: number) {
        while (this.scopes.length > 0 && this.scopes.at(-1)!.indent > indent) {
            this.scopes.pop()
        }
    }

    public normalizeName(name: string) {
        if (name.startsWith("\"")) {
            return name.slice(1, -1)
        } else if (name.startsWith("OperatorConstants")) {
            const operator = OPERATOR_CONSTANTS.get(name.slice(18))
            if (operator) return operator
        } else {
            const operator = OPERATOR_CONSTANTS.get(name)
            if (operator) return operator
        }

        return "??" + name
    }

    public getSite() {
        return this.path + ":" + (this.line + 1)
    }

    public parseLine() {
        const line = this.lines[this.line]
        if (line.trimEnd() == "") return

        const indent = line.match(/^\s*/)![0].length
        this.truncateScopesToIndent(indent)

        const explicitSymbol = line.match(/@symbol:\s?([\w.<>]+)/)
        if (explicitSymbol) {
            const name = explicitSymbol[1]
            const symbol = this.db.getSymbol(name)
            symbol.sites.push(this.getSite())
            this.scopes.push(new ScopeInfo(symbol, indent + 1))
            this.parseDeclarationValue()
            return
        }

        const tableClass = line.match(/(\w+) extends LazyTable/)
        if (tableClass) {
            const name = tableClass[1]
            let symbol: SymbolHandle

            if (name.endsWith("Api")) {
                symbol = this.db.getSymbol(name.slice(0, -3))
            } else if (name.endsWith("Prototype")) {
                symbol = this.db.getSymbol(name.slice(0, -9)).getChild("prototype")
                symbol.prototype ??= this.db.getSymbol("Table").getChild("prototype")
            } else {
                symbol = this.db.getSymbol(name)
            }

            symbol.sites.push(this.getSite())

            this.scopes.push(new ScopeInfo(symbol, indent + 1))
            return
        }

        if (line.includes("class GlobalScope extends Scope")) {
            const symbol = this.db.globalScope
            this.isGlobalScope = true
            this.scopes.push(new ScopeInfo(symbol, indent + 1))
            return
        }

        const declareGlobal = line.match(/\.declareGlobal\((".*?"|OperatorConstants\.\w+)/)
        if (declareGlobal) {
            const name = this.normalizeName(declareGlobal[1])
            const symbol = this.db.getSymbol(name)
            symbol.isEntry = true
            symbol.sites.push(this.getSite())
            this.scopes.push(new ScopeInfo(symbol, indent + 1))
            this.parseDeclarationValue()
            return
        }

        const declareProperty = line.match(/(\w+).declareProperty\((".*?"|OperatorConstants\.\w+)/)
        if (declareProperty) {
            const declaration = declareProperty[1]
            let parent: SymbolHandle
            const name = this.normalizeName(declareProperty[2])

            if (declaration == "this") {
                parent = this.getScope().symbol
            } else if (this.getScope().symbol == this.db.globalScope) {
                const globalPrototype = declaration.match(/^(\w+)Prototype$/)
                if (globalPrototype) {
                    parent = this.db.getSymbol(globalPrototype[1]).getChild("prototype")
                } else {
                    parent = this.db.getSymbol(declaration)
                }
            } else {
                parent = this.db.unknown
            }

            const symbol = parent.getChild(name)
            symbol.sites.push(this.getSite())
            this.scopes.push(new ScopeInfo(symbol, indent + 1))
            this.parseDeclarationValue()
            return
        }

        for (const factoryDefinition of this.project.symbolFactories ?? EMPTY_ARRAY) {
            const factory = line.match(factoryDefinition.getRegExp())
            if (factory) {
                const name = this.normalizeName(factory[1])
                const symbol = this.db.getSymbol(factoryDefinition.owner).getChild(name)
                symbol.sites.push(this.getSite())
                void (symbol.templates ??= []).push(this.db.getSymbol(factoryDefinition.template))
                return
            }
        }

        const scope = this.getScope()
        if (scope.symbol.isFunction) {
            const ensureArgumentTypes = line.match(/ensureArgumentTypes\(/)
            if (ensureArgumentTypes) {
                const overload = this.parseOverload()
                if (overload) {
                    if (scope.symbol.isPendingExplicitParameters) {
                        scope.symbol.overloads = null
                        scope.symbol.isPendingExplicitParameters = false
                    }

                    (scope.symbol.overloads ??= []).push(overload)
                }
                return
            }

            const prepareBinaryOperator = line.match(/prepareBinaryOperator\(/)
            if (prepareBinaryOperator) {
                const types = line.match(/([\w.<>]+)\.class,\s*([\w.<>]+)\.class/)
                if (types) {
                    if (scope.symbol.isPendingExplicitParameters) {
                        scope.symbol.overloads = null
                        scope.symbol.isPendingExplicitParameters = false
                    }

                    const left = this.db.findManagedSymbolByNativeClass(types[1])
                    const right = this.db.findManagedSymbolByNativeClass(types[2])

                    void (scope.symbol.overloads ??= []).push(...FunctionOverload.makeBinaryOperator(this.db, left, right))
                }
            }
        }

        const nativeHandleWrapper = line.match(/new NativeHandleWrapper<.*?>\("(\w+)", ([\w.]+)\.class/)
        if (nativeHandleWrapper) {
            const name = nativeHandleWrapper[1]
            const className = nativeHandleWrapper[2]
            if (name != className) {
                printWarn(`Native handle name mismatch: "${name}" != ${className}.class`)
            }

            const symbol = this.db.getSymbol(name).getChild("prototype")
            symbol.sites.push(this.getSite())
            this.scopes.push(new ScopeInfo(symbol, indent + 1).setNativeHandleWrapper())
            this.parseAdditionalInfo()
            return
        }

        const ensurePrototype = line.match(/(\w+).WRAPPER.ensurePrototype\(/)
        if (ensurePrototype) {
            const name = ensurePrototype[1]
            const symbol = this.db.getSymbol(name)
            symbol.isEntry = true
            symbol.sites.push(this.getSite())
            this.scopes.push(new ScopeInfo(symbol, indent + 1))
            this.parseAdditionalInfo()
            return
        }

        if (scope.isNativeHandleWrapper) {
            const addGetter = line.match(/addGetter\("(\w+)"/)
            if (addGetter) {
                const name = addGetter[1]
                const symbol = scope.symbol.getChild(name)
                symbol.sites.push(this.getSite())
                this.scopes.push(new ScopeInfo(symbol, indent + 1))
                this.parseAdditionalInfo()
                return
            }

            const addProperty = line.match(/addProperty\("(\w+)"/)
            if (addProperty) {
                const name = addProperty[1]
                const symbol = scope.symbol.getChild(name)
                symbol.sites.push(this.getSite())
                this.scopes.push(new ScopeInfo(symbol, indent + 1))
                this.parseAdditionalInfo()
                return
            }

            const addMethod = line.match(/addMethod\("(\w+)"/)
            if (addMethod) {
                const name = addMethod[1]
                const symbol = scope.symbol.getChild(name)
                symbol.sites.push(this.getSite())
                symbol.isFunction = true
                symbol.isPendingExplicitParameters = true

                const overload = this.parseOverload()
                if (overload) (symbol.overloads ??= []).push(overload)

                this.scopes.push(new ScopeInfo(symbol, indent + 1))
                this.parseDeclarationValue()
                return
            }
        }

        this.parseAdditionalInfo()
    }

    public parseOverload() {
        const line = this.lines[this.line]
        const parameters = line.match(/List.of\(((?:".*?"\s*,?\s*)+)\)/)
        if (!parameters) return null

        const parametersList = parameters[1].split(",").map(v => v.trim().slice(1, -1))

        let typesList
        const types = line.match(/List.of\(((?:[\w.<>]+\.class\s*,?\s*)+)\)/)
        if (types) {
            typesList = types[1].split(",").map(v => v.trim().slice(0, -6)).map(v => this.db.findManagedSymbolByNativeClass(v))
        } else {
            typesList = null
        }

        return new FunctionOverload(parametersList, typesList)
    }

    public parseDeclarationValue() {
        const line = this.lines[this.line]
        const scope = this.getScope()

        this.parseAdditionalInfo()

        const tablePrototype = line.match(/new ManagedTable\(([\w.]+)\)/)
        if (tablePrototype) {
            const prototypeAccess = tablePrototype[1]
            const globalPrototype = prototypeAccess.match(/(?:this|globalScope)\.(\w+?)Prototype/)
            if (globalPrototype) {
                const prototypeName = globalPrototype[1]
                scope.symbol.prototype = this.db.getSymbol(prototypeName).getChild("prototype")
            }
            return
        }

        const simpleFunction = line.match(/NativeFunction\.simple\(/)
        if (simpleFunction) {
            scope.symbol.isFunction = true
            scope.symbol.isPendingExplicitParameters = true

            const overload = this.parseOverload()
            if (overload) (scope.symbol.overloads ??= []).push(overload)

            return
        }

        const customFunction = line.match(/new NativeFunction/)
        if (customFunction) {
            scope.symbol.isFunction = true
            scope.symbol.isVariadicFunction = true
            scope.symbol.isPendingExplicitParameters = true
            return
        }
    }

    public parseAdditionalInfo() {
        let line = this.lines[this.line]
        const scope = this.getScope()

        const explicitValue = line.match(/@type:\s?([\w.<>]+)/)
        if (explicitValue) {
            const value = this.db.getSymbol(explicitValue[1])
            scope.symbol.value = value
        }

        const useTemplate = line.match(/@like:\s?([\w.<>]+)/)
        if (useTemplate) {
            const template = this.db.getSymbol(useTemplate[1])
            void (scope.symbol.templates ??= []).push(template)
        }

        const forceFunction = line.match(/@kind:\s?function/)
        if (forceFunction) {
            scope.symbol.isFunction = true
        }

        const summary = line.match(/@summary:\s([^@]*)/)
        if (summary) {
            scope.symbol.summary.push(summary[1])
        }

        const multilineSummary = line.match(/@summary\[\[(.*)/)
        if (multilineSummary) {
            if (multilineSummary[1].trim()) scope.symbol.summary.push(multilineSummary[1])
            this.line++
            for (; this.line < this.lines.length; this.line++) {
                line = this.lines[this.line]
                if (line.includes("]]")) break
                let trimmedLine = line.trim()
                if (trimmedLine.startsWith("* ")) {
                    scope.symbol.summary.push(trimmedLine.slice(2))
                } else if (trimmedLine.startsWith("// ")) {
                    scope.symbol.summary.push(trimmedLine.slice(3))
                } else if (trimmedLine.startsWith("//")) {
                    scope.symbol.summary.push(trimmedLine.slice(2))
                } else {
                    scope.symbol.summary.push(line)
                }
            }
        }
    }

    public parse() {
        while (this.line < this.lines.length) {
            this.parseLine()
            this.line++
        }
    }

    constructor(
        public readonly path: string,
        public readonly lines: string[],
        public readonly project: Project,
        public readonly db: SymbolDatabase,
    ) { }
}
