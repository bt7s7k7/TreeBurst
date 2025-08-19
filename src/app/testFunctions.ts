import { OPERATOR_EQ } from "../treeBurst/const"
import { evaluateInvocation, LABEL_EXCEPTION } from "../treeBurst/runtime/evaluateExpression"
import { ensureBoolean, GlobalScope, INTRINSIC, VOID } from "../treeBurst/runtime/GlobalScope"
import { NativeFunction } from "../treeBurst/runtime/NativeFunction"
import { Diagnostic } from "../treeBurst/support/Diagnostic"

export function initTestFunctions(scope: GlobalScope) {
    let counter = 0

    scope.declareGlobal("assert", NativeFunction.simple(scope, ["predicate"], (args, scope, result) => {
        const predicate = ensureBoolean(args[0], scope, result)
        if (result.label != null) return

        if (predicate == false) {
            result.value = new Diagnostic("Assertion failed", INTRINSIC)
            result.label = LABEL_EXCEPTION
            return
        }

        result.value = VOID
    }))

    scope.declareGlobal("assertEqual", NativeFunction.simple(scope, ["value", "pattern"], (args, scope, result) => {
        const [value, pattern] = args

        evaluateInvocation(value, value, OPERATOR_EQ, INTRINSIC, [pattern], scope, result)
        if (result.label != null) return

        const predicate = ensureBoolean(result.value, scope, result)
        if (result.label != null) return

        if (predicate == false) {
            result.value = new Diagnostic("Assertion failed", INTRINSIC)
            result.label = LABEL_EXCEPTION
            return
        }

        result.value = VOID
    }))

    scope.declareGlobal("increment", NativeFunction.simple(scope, [], (args, scope, result) => {
        counter++
        result.value = VOID
    }))

    scope.declareGlobal("getCounter", NativeFunction.simple(scope, [], (args, scope, result) => {
        result.value = counter
    }))
}
