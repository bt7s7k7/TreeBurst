import { LogMarker } from "../../prettyPrint/ObjectDescription"
import { ExpressionResult } from "./ExpressionResult"

export abstract class ManagedObject {
    public name: string | null = null

    public getProperty(name: string, result: ExpressionResult): boolean {
        return this.prototype?.getProperty(name, result) || false
    }

    public toString() {
        return this.name != null ? `[${this.name}]` : `[object ${this.prototype == null ? "null" : this.prototype.name == null ? "<anon>" : this.prototype.name}]`
    }

    public [LogMarker.CUSTOM](): any {
        return LogMarker.rawText(this.toString(), "cyan")
    }

    constructor(
        public readonly prototype: ManagedObject | null,
    ) { }
}
