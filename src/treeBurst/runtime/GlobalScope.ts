import { AbstractConstructor, Readwrite } from "../../comTypes/types"
import { convertCase, unreachable } from "../../comTypes/util"
import { LogMarker } from "../../prettyPrint/ObjectDescription"
import { OPERATOR_ADD, OPERATOR_AND, OPERATOR_AT, OPERATOR_BIT_AND, OPERATOR_BIT_NEG, OPERATOR_BIT_OR, OPERATOR_BIT_SHL, OPERATOR_BIT_SHR, OPERATOR_BIT_SHR_UNSIGNED, OPERATOR_BIT_XOR, OPERATOR_BOOLEAN, OPERATOR_COALESCE, OPERATOR_DIV, OPERATOR_ELSE, OPERATOR_EQ, OPERATOR_IS, OPERATOR_MOD, OPERATOR_MUL, OPERATOR_NEG, OPERATOR_NEQ, OPERATOR_NOT, OPERATOR_NUMBER, OPERATOR_OR, OPERATOR_POW, OPERATOR_SUB } from "../const"
import { Diagnostic } from "../support/Diagnostic"
import { Position } from "../support/Position"
import { Expression } from "../syntax/Expression"
import { evaluateExpression, evaluateInvocation, findProperty, getValueName, LABEL_EXCEPTION } from "./evaluateExpression"
import { ExpressionResult } from "./ExpressionResult"
import { ManagedArray } from "./ManagedArray"
import { ManagedObject } from "./ManagedObject"
import { ManagedTable } from "./ManagedTable"
import { ManagedValue } from "./ManagedValue"
import { NativeFunction, NativeHandler } from "./NativeFunction"
import { Scope } from "./Scope"
import { UnmanagedHandle } from "./UnmanagedHandle"

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
        result.label = LABEL_EXCEPTION
        return false
    }

    return true
}

export function ensureArgumentTypes<T extends any[] = any[]>(args: ManagedValue[], names: string[], types: (null | string | AbstractConstructor & { readonly [LogMarker.CUSTOM_NAME]: string })[], scope: Scope, result: ExpressionResult): T {
    if (types.length != names.length) unreachable()

    if (!verifyArguments(args, names, result)) return [] as never

    const results: any[] = []
    let errors: Diagnostic[] | null = null
    for (let i = 0; i < types.length; i++) {
        const type = types[i]
        if (type == null) {
            results[i] = args[i]
            continue
        }

        let value = args[i]
        const name = names[i]
        const passes = typeof type == "string" ? typeof value == type : value instanceof type

        if (!passes) {
            let conversionError: Diagnostic | null = null

            if (type == "boolean") {
                value = ensureBoolean(value, scope, result)
                if (result.label == null) {
                    results[i] = value
                    continue
                }

                if (result.label != LABEL_EXCEPTION || !(result.value instanceof Diagnostic)) return results as never
                conversionError = result.value
            }

            if (type == "number") {
                value = ensureNumber(value, scope, result)
                if (result.label == null) {
                    results[i] = value
                    continue
                }

                if (result.label != LABEL_EXCEPTION || !(result.value instanceof Diagnostic)) return results as never
                conversionError = result.value
            }

            (errors ??= []).push(new Diagnostic(
                `Wrong type for argument "${name}", expected "${typeof type == "string" ? convertCase(type, "camel", "pascal") : type[LogMarker.CUSTOM_NAME]}", but got "${getValueName(value)}"`,
                INTRINSIC,
                conversionError != null ? [conversionError] : undefined,
            ))
        }

        results[i] = value
    }

    if (errors) {
        result.value = new Diagnostic("Cannot invoke", INTRINSIC, errors)
        result.label = LABEL_EXCEPTION
    }

    return results as never
}

export function ensureExpression(value: ManagedValue, result: ExpressionResult) {
    if (value instanceof UnmanagedHandle && value.value instanceof Expression) return value.value
    result.value = new Diagnostic("Expected expression arguments", INTRINSIC)
    result.label = LABEL_EXCEPTION
    return null as never
}

export function ensureBoolean(value: ManagedValue, scope: Scope, result: ExpressionResult) {
    if (typeof value != "boolean") {
        evaluateInvocation(value, value, OPERATOR_BOOLEAN, INTRINSIC, [], scope, result)
        if (result.label != null) return false

        if (typeof value != "boolean") {
            evaluateInvocation(result.value, scope.globalScope.TablePrototype, OPERATOR_BOOLEAN, INTRINSIC, [], scope, result)
            if (result.label != null) return false
        }
    }

    return value as boolean
}

export function ensureNumber(value: ManagedValue, scope: Scope, result: ExpressionResult) {
    if (typeof value != "number") {
        evaluateInvocation(value, value, OPERATOR_NUMBER, INTRINSIC, [], scope, result)
        if (result.label != null) return 0

        if (typeof value != "boolean") {
            evaluateInvocation(result.value, scope.globalScope.TablePrototype, OPERATOR_NUMBER, INTRINSIC, [], scope, result)
            if (result.label != null) return 0
        }
    }

    return value as number
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
        const [self] = ensureArgumentTypes(args, ["this"], ["boolean"], scope, result)
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
    Number_neg(args, scope, result) {
        const [self] = ensureArgumentTypes(args, ["this"], ["number"], scope, result)
        if (result.label != null) return

        result.value = -self
    },
    Number_bitNeg(args, scope, result) {
        const [self] = ensureArgumentTypes(args, ["this"], ["number"], scope, result)
        if (result.label != null) return

        result.value = ~self
    },
    k_if(args, scope, result) {
        for (let i = 0; i < args.length; i += 2) {
            if (args.length - i < 2) {
                const elseValue = ensureExpression(args[i], result)
                if (result.label != null) return
                evaluateExpression(elseValue, scope, result)
                return
            }

            const predicate = ensureExpression(args[i], result)
            if (result.label != null) return
            const thenValue = ensureExpression(args[i + 1], result)
            if (result.label != null) return

            evaluateExpression(predicate, scope, result)
            if (result.label != null) return

            const predicateValue = ensureBoolean(result.value, scope, result)
            if (result.label != null) return false

            if (predicateValue) {
                evaluateExpression(thenValue, scope, result)
                return
            }
        }

        result.value = VOID
    },
    Table_and(args, scope, result) {
        if (!verifyArguments(args, ["this", "other"], result)) return
        const predicateResult = args[0]
        const predicateValue = ensureBoolean(predicateResult, scope, result)
        if (result.label != null) return

        const alternative = ensureExpression(args[1], result)
        if (result.label != null) return

        if (predicateValue) {
            evaluateExpression(alternative, scope, result)
        } else {
            result.value = predicateResult
        }
    },
    Table_or(args, scope, result) {
        if (!verifyArguments(args, ["this", "other"], result)) return
        const predicateResult = args[0]
        const predicateValue = ensureBoolean(predicateResult, scope, result)
        if (result.label != null) return

        const alternative = ensureExpression(args[1], result)
        if (result.label != null) return

        if (!predicateValue) {
            evaluateExpression(alternative, scope, result)
        } else {
            result.value = predicateResult
        }
    },
    Table_coalesce(args, scope, result) {
        if (!verifyArguments(args, ["this", "other"], result)) return
        const predicateResult = args[0]
        if (result.label != null) return

        const alternative = ensureExpression(args[1], result)
        if (result.label != null) return

        if (predicateResult == null || predicateResult == VOID) {
            evaluateExpression(alternative, scope, result)
        } else {
            result.value = predicateResult
        }
    },
    Table_else(args, scope, result) {
        if (!verifyArguments(args, ["this", "other"], result)) return
        const predicateResult = args[0]
        if (result.label != null) return

        const alternative = ensureExpression(args[1], result)
        if (result.label != null) return

        if (predicateResult == VOID) {
            evaluateExpression(alternative, scope, result)
        } else {
            result.value = predicateResult
        }
    },
    Array_at(args, scope, result) {
        if (args.length <= 2) {
            let [self, index] = ensureArgumentTypes<[ManagedArray, number]>(args, ["this", "index"], [ManagedArray, "number"], scope, result)
            if (result.label != null) return

            index = self.normalizeIndex(index, result)
            if (result.label != null) return

            result.value = self.elements[index]
        } else {
            let [self, index, value] = ensureArgumentTypes<[ManagedArray, number, any]>(args, ["this", "index", "value"], [ManagedArray, "number", null], scope, result)
            if (result.label != null) return

            index = self.normalizeIndex(index, result)
            if (result.label != null) return

            self.elements[index] = value
            result.value = value
        }
    },
} satisfies Record<string, NativeHandler>


const _defaultFallback = ((name, a, b, scope, result) => {
    result.value = new Diagnostic(`Operands "${getValueName(a)}" and "${getValueName(b)}" do not support operator "${name}"`, INTRINSIC)
    result.label = LABEL_EXCEPTION
}) satisfies Parameters<typeof _makeOperatorFallback>[1]
function _makeOperatorFallback(name: string, fallback: (name: string, a: ManagedValue, b: ManagedValue, scope: Scope, result: ExpressionResult) => void = _defaultFallback) {
    _OPERATOR_FALLBACKS[name] = function (args, scope, result) {
        if (!verifyArguments(args, ["this", "other"], result)) return
        const [self, a, b] = args

        if (args.length > 2) {
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


const _NUMBER_OPERATORS: Record<string, NativeHandler> = {}
function _makeNumberOperator(name: string, operator: (a: number, b: number) => any) {
    _NUMBER_OPERATORS[name] = function (args, scope, result) {
        if (args.length > 2) {
            const [_, a, b] = ensureArgumentTypes(args, ["this", "left", "right"], [null, "number", "number"], scope, result)
            if (result.label != null) return

            result.value = operator(a, b)
            return
        }

        const [a, b] = ensureArgumentTypes(args, ["this", "right"], ["number", "number"], scope, result)
        if (result.label != null) return

        result.value = operator(a, b)
    }
}

_makeNumberOperator(OPERATOR_ADD, (a, b) => a + b)
_makeNumberOperator(OPERATOR_SUB, (a, b) => a - b)
_makeNumberOperator(OPERATOR_MUL, (a, b) => a * b)
_makeNumberOperator(OPERATOR_DIV, (a, b) => a / b)
_makeNumberOperator(OPERATOR_MOD, (a, b) => a % b)
_makeNumberOperator(OPERATOR_POW, (a, b) => a ** b)

_makeNumberOperator(OPERATOR_BIT_XOR, (a, b) => a ^ b)
_makeNumberOperator(OPERATOR_BIT_AND, (a, b) => a & b)
_makeNumberOperator(OPERATOR_BIT_OR, (a, b) => a | b)
_makeNumberOperator(OPERATOR_BIT_SHL, (a, b) => a << b)
_makeNumberOperator(OPERATOR_BIT_SHR, (a, b) => a >> b)
_makeNumberOperator(OPERATOR_BIT_SHR_UNSIGNED, (a, b) => a >>> b)

export class GlobalScope extends Scope {
    public readonly TablePrototype = new ManagedTable(null)
    public readonly Table = this.declareGlobal("Table", new ManagedTable(this.TablePrototype))

    public readonly FunctionPrototype = new ManagedTable(this.TablePrototype)
    public readonly Function = this.declareGlobal("Function", new ManagedTable(this.TablePrototype))

    public readonly NumberPrototype = new ManagedTable(this.TablePrototype)
    public readonly Number = this.declareGlobal("Number", new ManagedTable(this.TablePrototype))

    public readonly StringPrototype = new ManagedTable(this.TablePrototype)
    public readonly String = this.declareGlobal("String", new ManagedTable(this.TablePrototype))

    public readonly BooleanPrototype = new ManagedTable(this.TablePrototype)
    public readonly Boolean = this.declareGlobal("Boolean", new ManagedTable(this.TablePrototype))

    public readonly ArrayPrototype = new ManagedTable(this.TablePrototype)
    public readonly Array = this.declareGlobal("Array", new ManagedTable(this.TablePrototype))

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
        this.Function.declareProperty("prototype", this.FunctionPrototype) || unreachable()
        this.Number.declareProperty("prototype", this.NumberPrototype) || unreachable()
        this.String.declareProperty("prototype", this.StringPrototype) || unreachable()
        this.Boolean.declareProperty("prototype", this.BooleanPrototype) || unreachable()
        this.Array.declareProperty("prototype", this.ArrayPrototype) || unreachable()

        this.Table.declareProperty("new", new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Table_new)) || unreachable()

        for (const [name, operator] of Object.entries(_OPERATOR_FALLBACKS)) {
            this.TablePrototype.declareProperty(name, new NativeFunction(this.FunctionPrototype, ["this", "a", "b"], operator)) || unreachable()
        }
        this.TablePrototype.declareProperty(OPERATOR_IS, new NativeFunction(this.FunctionPrototype, ["this", "a", "b"], _HANDLERS.Table_is)) || unreachable()

        this.TablePrototype.declareProperty(OPERATOR_AND, new NativeFunction(this.FunctionPrototype, ["this", "other"], _HANDLERS.Table_and)) || unreachable()
        this.TablePrototype.declareProperty(OPERATOR_OR, new NativeFunction(this.FunctionPrototype, ["this", "other"], _HANDLERS.Table_or)) || unreachable()
        this.TablePrototype.declareProperty(OPERATOR_COALESCE, new NativeFunction(this.FunctionPrototype, ["this", "other"], _HANDLERS.Table_coalesce)) || unreachable()
        this.TablePrototype.declareProperty(OPERATOR_ELSE, new NativeFunction(this.FunctionPrototype, ["this", "other"], _HANDLERS.Table_else)) || unreachable()

        this.BooleanPrototype.declareProperty(OPERATOR_NOT, new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Boolean_not)) || unreachable()
        this.TablePrototype.declareProperty(OPERATOR_NOT, new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Table_not)) || unreachable()
        this.TablePrototype.declareProperty(OPERATOR_BOOLEAN, new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Table_boolean)) || unreachable()

        this.declareGlobal("@if", new NativeFunction(this.FunctionPrototype, [], _HANDLERS.k_if)) || unreachable()

        for (const [name, operator] of Object.entries(_NUMBER_OPERATORS)) {
            this.NumberPrototype.declareProperty(name, new NativeFunction(this.FunctionPrototype, ["this", "other"], operator)) || unreachable()
        }

        this.NumberPrototype.declareProperty(OPERATOR_NEG, new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Number_neg)) || unreachable()
        this.NumberPrototype.declareProperty(OPERATOR_BIT_NEG, new NativeFunction(this.FunctionPrototype, ["this"], _HANDLERS.Number_bitNeg)) || unreachable()

        this.ArrayPrototype.declareProperty(OPERATOR_AT, new NativeFunction(this.FunctionPrototype, ["this", "other"], _HANDLERS.Array_at)) || unreachable()

        this.declareGlobal("true", true)
        this.declareGlobal("false", false)
        this.declareGlobal("null", null)
        this.declareGlobal("void", VOID)
    }
}

