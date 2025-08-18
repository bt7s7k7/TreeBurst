import { LogMarker } from "../../prettyPrint/ObjectDescription"
import { Position } from "../support/Position"
import { recordClass } from "../support/recordClass"

export abstract class Expression extends recordClass(undefined, (position: Position) => ({ position })) { }

export namespace Expression {
    export class NumberLiteral extends recordClass(Expression, (value: number) => ({ value })) { public static readonly [LogMarker.CUSTOM_NAME] = "NumberLiteral" }
    export class StringLiteral extends recordClass(Expression, (value: string) => ({ value })) { public static readonly [LogMarker.CUSTOM_NAME] = "StringLiteral" }
    export class ArrayLiteral extends recordClass(Expression, (elements: Expression[]) => ({ elements })) { public static readonly [LogMarker.CUSTOM_NAME] = "ArrayLiteral" }
    export class Identifier extends recordClass(Expression, (name: string) => ({ name })) { public static readonly [LogMarker.CUSTOM_NAME] = "Identifier" }
    export class VariableDeclaration extends recordClass(Expression, (declaration: Expression) => ({ declaration })) { public static readonly [LogMarker.CUSTOM_NAME] = "VariableDeclaration" }
    export class Assignment extends recordClass(Expression, (receiver: Expression, value: Expression) => ({ receiver, value })) { public static readonly [LogMarker.CUSTOM_NAME] = "Assignment" }
    export class MemberAccess extends recordClass(Expression, (receiver: Expression, member: string) => ({ receiver, member })) { public static readonly [LogMarker.CUSTOM_NAME] = "MemberAccess" }
    export class Group extends recordClass(Expression, (children: Expression[]) => ({ children })) { public static readonly [LogMarker.CUSTOM_NAME] = "Group" }
    export class FunctionDeclaration extends recordClass(Expression, (parameters: string[], body: Expression) => ({ parameters, body })) { public static readonly [LogMarker.CUSTOM_NAME] = "FunctionDeclaration" }
    export class Invocation extends recordClass(Expression, (target: Expression, args: Expression[]) => ({ target, args })) {
        public static readonly [LogMarker.CUSTOM_NAME] = "Invocation"

        public static makeMethodCall(position: Position, receiver: Expression, method: string, args: Expression[]) {
            return new Invocation(position, new MemberAccess(position, receiver, method), args)
        }
    }
}
