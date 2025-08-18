import { ExpressionResult } from "./ExpressionResult"
import { ManagedObject } from "./ManagedObject"
import { ManagedValue } from "./ManagedValue"

export class ManagedTable extends ManagedObject {
    protected readonly _properties = new Map<string, ManagedValue>()

    public override getProperty(name: string, result: ExpressionResult): boolean {
        if (this._properties.has(name)) {
            result.value = this._properties.get(name)!
            return true
        }

        return super.getProperty(name, result)
    }

    public declareProperty(name: string, value: ManagedValue): boolean {
        if (this._properties.has(name)) return false
        this._properties.set(name, value)

        if (this.name != null && value instanceof ManagedObject && value.name == null) {
            value.name = this.name + "." + name
        }

        return true
    }

    public setProperty(name: string, value: ManagedValue): boolean {
        if (!this._properties.has(name)) return false
        this._properties.set(name, value)
        return true
    }
}
