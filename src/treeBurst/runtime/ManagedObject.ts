import { ExpressionResult } from "./ExpressionResult"

export abstract class ManagedObject {
    public name: string | null = null

    public getProperty(name: string, result: ExpressionResult): boolean {
        return this.prototype?.getProperty(name, result) || false
    }

    constructor(
        public readonly prototype: ManagedObject | null,
    ) { }
}
