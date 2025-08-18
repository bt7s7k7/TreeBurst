import { AbstractConstructor, Readwrite } from "../../comTypes/types"
import { convertCase, unreachable } from "../../comTypes/util"
import { LogMarker } from "../../prettyPrint/ObjectDescription"
import { OPERATOR_ADD, OPERATOR_BOOLEAN, OPERATOR_DIV, OPERATOR_EQ, OPERATOR_IS, OPERATOR_MOD, OPERATOR_MUL, OPERATOR_NEQ, OPERATOR_NOT, OPERATOR_POW, OPERATOR_SUB } from "../const"
import { Diagnostic } from "../support/Diagnostic"
import { Position } from "../support/Position"
import { evaluateInvocation, findProperty, getValueName, LABEL_EXCEPTION } from "./evaluateExpression"
import { ExpressionResult } from "./ExpressionResult"
import { ManagedObject } from "./ManagedObject"
import { ManagedTable } from "./ManagedTable"
import { ManagedValue } from "./ManagedValue"
import { NativeFunction, NativeHandler } from "./NativeFunction"
import { Scope } from "./Scope"

export const VOID = new class Void {
    public static readonly [LogMarker.CUSTOM_NAME] = "Void"
    public [LogMarker.CUSTOM]() { return LogMarker.rawText("void", "gray") }
}

export const INTRINSIC = Position.createSpecial("INTRINSIC")

export function verifyArguments(args: ManagedValue[], names: string[], result: ExpressionResult) {
    if (args.length < names.length) {
        result.value = new Diagnostic(
            `Expected ${names.length} arguments, but got ${args.length}`, INTRINSIC,
            names.slice(args.length).map(name => new Diagnostic(`Missing argument "${name}"`, INTRINSIC)),
        )
        return false
    }

    return true
}

export function ensureArgumentTypes<T extends any[] = any[]>(args: ManagedValue[], names: string[], types: (string | AbstractConstructor & { readonly [LogMarker.CUSTOM_NAME]: string })[], result: ExpressionResult): T {
    if (!verifyArguments(args, names, result)) return [] as any as T

    const results: any[] = []
    let errors: Diagnostic[] | null = null
    for (let i = 0; i < args.length; i++) {
        const value = args[i]
        const type = types[i]
        const name = names[i]
        const passes = typeof type == "string" ? typeof value == type : value instanceof type

        if (!passes) {
            (errors ??= []).push(new Diagnostic(`Wrong type for argument "${name}", expected "${typeof type == "string" ? convertCase(type, "camel", "pascal") : type[LogMarker.CUSTOM_NAME]}", but got "${getValueName(value)}"`, INTRINSIC))
        }

        results[i] = value
    }

    if (errors) {
        result.value = new Diagnostic("Cannot invoke", INTRINSIC, errors)
        result.label = LABEL_EXCEPTION
    }

    return results as any as T
}

const _HANDLERS = {
    Table_new(args, scope, result) {
        if (!verifyArguments(args, ["this"], result)) return
        const [self] = args

        if (!findProperty(self, self, "prototype", scope, result)) {
            result.value = new Diagnostic(`Cannot find a prototype on receiver`, INTRINSIC)
            result.label = LABEL_EXCEPTION
            return
        }

        const prototype = result.value
        if (!(prototype instanceof ManagedTable)) {
            result.value = new Diagnostic(`Prototype must be a Table`, INTRINSIC)
            result.label = LABEL_EXCEPTION
            return
        }

        result.value = new ManagedTable(prototype)
    },
    Boolean_not(args, scope, result) {
        const [self] = ensureArgumentTypes(args, ["this"], ["boolean"], result)
        if (result.label != null) return

        result.value = !self
    },
    Table_not(args, scope, result) {
        if (!verifyArguments(args, ["this"], result)) return
        const [self] = args

        evaluateInvocation(self, self, OPERATOR_BOOLEAN, INTRINSIC, [], scope, result)
        if (result.label != null) return
        evaluateInvocation(result.value, result.value, OPERATOR_NOT, INTRINSIC, [], scope, result)
    },
    Table_boolean(args, scope, result) {
        if (!verifyArguments(args, ["this"], result)) return
        const [self] = args

        if (self === false || self === 0 || self === "" || self === null || self === VOID) {
            result.value = false
            return
        }

        result.value = true
    },
    Table_is(args, scope, result) {
        if (!verifyArguments(args, ["this", "other"], result)) return
        const [self, a] = args

        result.value = self == a
    },
} satisfies Record<string, NativeHandler>

const _defaultFallback = ((name, a, b, scope, result) => {
    result.value = new Diagnostic(`This pair of operands do not support operator "${name}"`, INTRINSIC)
    result.label = LABEL_EXCEPTION
}) satisfies Parameters<typeof _makeOperatorFallback>[1]
function _makeOperatorFallback(name: string, fallback: (name: string, a: ManagedValue, b: ManagedValue, scope: Scope, result: ExpressionResult) => void = _defaultFallback) {
    _OPERATOR_FALLBACKS[name] = function (args, scope, result) {
        if (!verifyArguments(args, ["this", "other"], result)) return
        const [self, a, b] = args

        if (self == VOID) {
            fallback(name, a, b, scope, result)
            return
        }

        evaluateInvocation(VOID, a, name, INTRINSIC, [self, a], scope, result)
    }
}
const _OPERATOR_FALLBACKS: Record<string, NativeHandler> = {}

_makeOperatorFallback(OPERATOR_EQ, (name, a, b, scope, result) => result.value = a == b)
_makeOperatorFallback(OPERATOR_NEQ, (name, a, b, scope, result) => {
    evaluateInvocation(a, a, OPERATOR_EQ, INTRINSIC, [b], scope, result)
    if (result.label != null) return
    evaluateInvocation(result.value, result.value, OPERATOR_NOT, INTRINSIC, [], scope, result)
})

_makeOperatorFallback(OPERATOR_ADD)
_makeOperatorFallback(OPERATOR_SUB)
_makeOperatorFallback(OPERATOR_MUL)
_makeOperatorFallback(OPERATOR_DIV)
_makeOperatorFallback(OPERATOR_MOD)
_makeOperatorFallback(OPERATOR_POW)


export class GlobalScope extends Scope {
    public readonly TablePrototype = new ManagedTable(null)

    public readonly FunctionPrototype = new ManagedTable(this.TablePrototype)
    public readonly Function = this.declareGlobal("Function", new ManagedTable(this.TablePrototype))

    public readonly Table = this.declareGlobal("Table", new ManagedTable(this.TablePrototype))

    public readonly NumberPrototype = new ManagedTable(this.TablePrototype)
    public readonly Number = this.declareGlobal("Number", new ManagedTable(this.TablePrototype))

    public readonly StringPrototype = new ManagedTable(this.TablePrototype)
    public readonly String = this.declareGlobal("String", new ManagedTable(this.TablePrototype))

    public readonly BooleanPrototype = new ManagedTable(this.TablePrototype)
    public readonly Boolean = this.declareGlobal("Boolean", new ManagedTable(this.TablePrototype))

    public declareGlobal<T extends ManagedValue>(name: string, value: T) {
        const variable = this.declareVariable(name)
        if (variable == null) {
            throw new RangeError(`Duplicate declaration of global "${name}"`)
        }

        variable.value = value

        if (value instanceof ManagedObject && value.name == null) {
            value.name = name
        }

        return value
    }

    constructor() {
        super(null, null!);
        (this as Readwrite<this>).globalScope = this

        this.Table.declareProperty("prototype", this.TablePrototype) || unreachable()
        this.Table.declareProperty("new", new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Table_new)) || unreachable()

        for (const [name, operator] of Object.entries(_OPERATOR_FALLBACKS)) {
            this.TablePrototype.declareProperty(name, new NativeFunction(this.FunctionPrototype, ["this", "a", "b"], operator)) || unreachable()
        }
        this.TablePrototype.declareProperty(OPERATOR_IS, new NativeFunction(this.FunctionPrototype, ["this", "a", "b"], _HANDLERS.Table_is)) || unreachable()

        this.BooleanPrototype.declareProperty(OPERATOR_NOT, new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Boolean_not)) || unreachable()
        this.TablePrototype.declareProperty(OPERATOR_NOT, new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Table_not)) || unreachable()
        this.TablePrototype.declareProperty(OPERATOR_BOOLEAN, new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Table_boolean)) || unreachable()

        this.declareGlobal("true", true)
        this.declareGlobal("false", false)
        this.declareGlobal("null", null)
        this.declareGlobal("void", VOID)
    }
}

