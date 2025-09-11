import { SymbolDatabase } from "./SymbolDatabase"
import { SymbolHandle } from "./SymbolHandle"


export class FunctionOverload {
    public isVariadic = false

    public withVariadic() {
        this.isVariadic = true
        return this
    }

    constructor(
        public readonly parameters: string[],
        public readonly types: SymbolHandle[] | null,
    ) { }

    public static makeBinaryOperator(db: SymbolDatabase, left: SymbolHandle, right: SymbolHandle) {
        return [
            new FunctionOverload(["this", "left", "right"], [db.getSymbol("any"), left, right]),
            new FunctionOverload(["this", "right"], [left, right]),
        ]
    }
}
