import { unreachable } from "../../comTypes/util"
import { Diagnostic } from "../support/Diagnostic"
import { Position } from "../support/Position"
import { Expression } from "../syntax/Expression"
import { ExpressionResult } from "./ExpressionResult"
import { VOID } from "./GlobalScope"
import { ManagedFunction } from "./ManagedFunction"
import { ManagedObject } from "./ManagedObject"
import { ManagedTable } from "./ManagedTable"
import { ManagedValue } from "./ManagedValue"
import { Scope } from "./Scope"
import { UnmanagedHandle } from "./UnmanagedHandle"

export const LABEL_RETURN = "!return"
export const LABEL_EXCEPTION = "!exception"

export function evaluateExpressions(children: Expression[], scope: Scope, result: ExpressionResult): ManagedValue[] | null {
    const results: ManagedValue[] = []

    for (const child of children) {
        evaluateExpression(child, scope, result)

        if (result.label != null) {
            return null
        }

        results.push(result.value)
    }

    return results
}

export function evaluateExpressionBlock(children: Expression[], scope: Scope, result: ExpressionResult) {
    for (const child of children) {
        evaluateExpression(child, scope, result)

        if (result.label != null) {
            return
        }
    }
}

export function findProperty(receiver: ManagedValue, container: ManagedValue, name: string, scope: Scope, result: ExpressionResult): boolean {
    if (container == null || container == VOID) {
        return findProperty(receiver, scope.globalScope.TablePrototype, name, scope, result)
    }

    if (typeof container == "number") {
        return findProperty(receiver, scope.globalScope.NumberPrototype, name, scope, result)
    }

    if (typeof container == "string") {
        return findProperty(receiver, scope.globalScope.StringPrototype, name, scope, result)
    }

    if (typeof container == "boolean") {
        return findProperty(receiver, scope.globalScope.BooleanPrototype, name, scope, result)
    }

    if (container instanceof ManagedObject) {
        if (container.getProperty(name, result)) {
            return true
        }

        return false
    }

    return false
}

export function evaluateDeclaration(declaration: Expression, value: ManagedValue, scope: Scope, result: ExpressionResult) {
    if (declaration instanceof Expression.Identifier) {
        const variable = scope.declareVariable(declaration.name)
        if (variable == null) {
            result.value = new Diagnostic(`Duplicate declaration of variable "${declaration.name}"`, declaration.position)
            result.label = LABEL_EXCEPTION
            return
        }

        result.value = variable.value = value
        return
    } else {
        result.value = new Diagnostic(`Invalid declaration target`, declaration.position)
        result.label = LABEL_EXCEPTION
        return
    }
}

export function getValueName(container: ManagedValue) {
    if (container instanceof ManagedObject && container.name != null) {
        return `${container.name}`
    } else if (container instanceof ManagedFunction) {
        return `<function>`
    } else if (container instanceof ManagedTable) {
        return `<table>`
    } else if (container instanceof UnmanagedHandle) {
        return `<unmanaged>`
    } else {
        return `<${typeof container}>`
    }
}

export function evaluateInvocation(receiver: ManagedValue, container: ManagedValue, function_1: ManagedValue, position: Position, args: ManagedValue[], scope: Scope, result: ExpressionResult) {
    if (typeof function_1 == "string") {
        if (!findProperty(container, container, function_1, scope, result)) {
            result.value = new Diagnostic(`Cannot find method "${getValueName(container)}.${function_1}"`, position)
            result.label = LABEL_EXCEPTION
            return
        }

        function_1 = result.value

    }

    if (!(function_1 instanceof ManagedFunction)) {
        result.value = new Diagnostic(`Target "${getValueName(container)}" is not callable`, position)
        result.label = LABEL_EXCEPTION
        return
    }

    if (function_1.parameters.length > 0 && function_1.parameters[0] == "this") {
        args.unshift(receiver)
    }

    function_1.invoke(args, scope, result)

    if (result.label == LABEL_EXCEPTION && result.value instanceof Diagnostic) {
        result.value = new Diagnostic("Invocation failed", position, [result.value])
    }
}

export function evaluateExpression(expression: Expression, scope: Scope, result: ExpressionResult) {
    if (expression instanceof Expression.NumberLiteral || expression instanceof Expression.StringLiteral) {
        result.value = expression.value
        return
    }

    if (expression instanceof Expression.Identifier) {
        const variable = scope.findVariable(expression.name)

        if (variable == null) {
            result.value = new Diagnostic(`Cannot find variable "${expression.name}"`, expression.position)
            result.label = LABEL_EXCEPTION
            return
        }

        result.value = variable.value
        return
    }

    if (expression instanceof Expression.VariableDeclaration) {
        const declaration = expression.declaration
        evaluateDeclaration(declaration, VOID, scope, result)
        return
    }

    if (expression instanceof Expression.Invocation) {
        const { args, position, target } = expression



        let receiverValue: ManagedValue
        let functionValue: ManagedValue
        let functionName: string

        if (target instanceof Expression.MemberAccess) {
            evaluateExpression(target.receiver, scope, result)
            if (result.label != null) return

            receiverValue = result.value
            functionValue = target.member
            functionName = target.member
        } else if (target instanceof Expression.Identifier) {
            evaluateExpression(target, scope, result)
            if (result.label != null) return

            receiverValue = VOID
            functionValue = result.value
            functionName = target.name
        } else {
            result.value = new Diagnostic(`Invalid invocation target`, target.position)
            result.label = LABEL_EXCEPTION
            return
        }

        if (functionName.startsWith("@")) {
            evaluateInvocation(receiverValue, receiverValue, functionValue, position, args.map(v => new UnmanagedHandle(scope.globalScope.TablePrototype, v)), scope, result)
        } else {
            const argValues = evaluateExpressions(args, scope, result)
            if (argValues == null) {
                return
            }

            evaluateInvocation(receiverValue, receiverValue, functionValue, position, argValues, scope, result)
        }

        return
    }

    if (expression instanceof Expression.Assignment) {
        const { receiver, value, position } = expression

        evaluateExpression(value, scope, result)
        if (result.label != null) return

        const valueValue = result.value

        if (receiver instanceof Expression.Identifier) {
            const variable = scope.findVariable(receiver.name)
            if (variable == null) {
                result.value = new Diagnostic(`Cannot find variable "${receiver.name}"`, position)
                result.label = LABEL_EXCEPTION
                return
            }

            variable.value = valueValue
            return
        } else if (receiver instanceof Expression.VariableDeclaration) {
            evaluateDeclaration(receiver.declaration, valueValue, scope, result)
            return
        } else {
            result.value = new Diagnostic(`Invalid assignment target`, receiver.position)
            result.label = LABEL_EXCEPTION
            return
        }
    }

    if (expression instanceof Expression.Group) {
        evaluateExpressionBlock(expression.children, scope, result)
        return
    }

    if (expression instanceof Expression.MemberAccess) {
        evaluateExpression(expression.receiver, scope, result)
        if (result.label != null) return
        const receiverValue = result.value

        if (!findProperty(receiverValue, receiverValue, expression.member, scope, result)) {
            result.value = new Diagnostic(`Cannot find property "${getValueName(receiverValue)}.${expression.member}"`, expression.position)
            result.label = LABEL_EXCEPTION
            return
        }

        return
    }

    unreachable()
}
