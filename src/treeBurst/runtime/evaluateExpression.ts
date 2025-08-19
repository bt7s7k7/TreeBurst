import { convertCase, unreachable } from "../../comTypes/util"
import { Diagnostic } from "../support/Diagnostic"
import { Position } from "../support/Position"
import { Expression } from "../syntax/Expression"
import { ExpressionResult } from "./ExpressionResult"
import { VOID } from "./GlobalScope"
import { ManagedArray } from "./ManagedArray"
import { ManagedFunction } from "./ManagedFunction"
import { ManagedObject } from "./ManagedObject"
import { ManagedTable } from "./ManagedTable"
import { ManagedValue } from "./ManagedValue"
import { Scope } from "./Scope"
import { ScriptFunction } from "./ScriptFunction"
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
        if (value instanceof ManagedObject && value.name == null) {
            value.name = declaration.name
        }
        return
    } else if (declaration instanceof Expression.MemberAccess) {
        evaluateExpression(declaration.receiver, scope, result)
        if (result.label != null) return null
        const receiver = result.value

        if (!(receiver instanceof ManagedTable)) {
            result.value = new Diagnostic(`Cannot declare properties on "${getValueName(receiver)}"`, declaration.position)
            result.label = LABEL_EXCEPTION
            return
        }

        if (!receiver.declareProperty(declaration.member, value)) {
            result.value = new Diagnostic(`Property "${declaration.member}" is already defined`, declaration.position)
            result.label = LABEL_EXCEPTION
            return
        }

        result.value = value
        return
    } else {
        result.value = new Diagnostic(`Invalid declaration target`, declaration.position)
        result.label = LABEL_EXCEPTION
        return
    }
}

export function getValueName(container: ManagedValue) {
    if (container == VOID) {
        return "void"
    } else if (container == null) {
        return "null"
    } else if (container instanceof ManagedObject) {
        return container.toString()
    } else {
        return convertCase(typeof container, "camel", "pascal")
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
        } else {
            evaluateExpression(target, scope, result)
            if (result.label != null) return

            receiverValue = VOID
            functionValue = result.value
            functionName = target instanceof Expression.Identifier ? target.name : ""
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
        } else if (receiver instanceof Expression.MemberAccess) {
            evaluateExpression(receiver.receiver, scope, result)
            if (result.label != null) return null
            const receiver_1 = result.value

            if (!(receiver_1 instanceof ManagedTable)) {
                result.value = new Diagnostic(`Cannot set properties on "${getValueName(receiver_1)}"`, receiver.position)
                result.label = LABEL_EXCEPTION
                return
            }

            if (!receiver_1.setProperty(receiver.member, valueValue)) {
                result.value = new Diagnostic(`Property "${receiver.member}" is not defined on "${getValueName(receiver_1)}"`, receiver.position)
                result.label = LABEL_EXCEPTION
                return
            }

            result.value = valueValue
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

    if (expression instanceof Expression.FunctionDeclaration) {
        result.value = new ScriptFunction(scope.globalScope.FunctionPrototype, expression.parameters, expression.body, scope)
        return
    }

    if (expression instanceof Expression.ArrayLiteral) {
        const elements = evaluateExpressions(expression.elements, scope, result)
        if (elements == null) return null
        result.value = new ManagedArray(scope.globalScope.ArrayPrototype, elements)
        return
    }

    unreachable()
}
