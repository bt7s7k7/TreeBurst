import { EMPTY_ARRAY } from "../../comTypes/const"
import { LogMarker } from "../../prettyPrint/ObjectDescription"
import { _PositionFormattingOptions, Position } from "./Position"

/** Error message produced by the parser containing a position. */
export class Diagnostic {
    public format(options?: _PositionFormattingOptions) {
        if (this.additionalErrors.length != 0) {
            const result: string[] = []
            this._formatRecursive(result, options)
            return result.join("\n")
        }

        return this.position.format(this.message, options)
    }

    protected _formatRecursive(target: string[], options: _PositionFormattingOptions) {
        target.push(this.position.format(this.message, options))

        const childOptions: _PositionFormattingOptions = { ...options, indent: "    " + (options?.indent ?? "") }
        for (const additionalError of this.additionalErrors) {
            additionalError._formatRecursive(target, childOptions)
        }
    }

    public [LogMarker.CUSTOM]() {
        return LogMarker.rawText(this.format({ colors: true }), "white", { indent: true })
    }

    constructor(
        public readonly message: string,
        public readonly position: Position,
        public readonly additionalErrors: readonly Diagnostic[] = EMPTY_ARRAY,
    ) { }
}
