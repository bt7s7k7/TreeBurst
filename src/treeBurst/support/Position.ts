import { Readwrite } from "../../comTypes/types"
import { LogMarker, ObjectDescription } from "../../prettyPrint/ObjectDescription"
import { FORMAT } from "../../textFormat/Formatter"
import { InputDocument } from "./InputDocument"

/** A range of characters in a document, used in {@link Diagnostic}. */
export class Position {
    protected _line: number | null = null
    public get line() { return this._line != null ? this._line : (this._resolve(), this._line!) }
    protected _char: number | null = null
    public get char() { return this._char != null ? this._char : (this._resolve(), this._char!) }

    public get offset() { return this.index }
    public get end() { return this.index + this.length }

    public after() {
        return new Position(this.document, this.index + this.length, 1)
    }

    public setLength(length: number) {
        (this as Readwrite<this>).length = length
        this._line = null
        this._char = null
        return this
    }

    protected _resolve() {
        if (this._line != null && this._char != null) return

        const cursor = this.document.getCursorAtIndex(this.index)

        this._line = cursor.line
        this._char = cursor.char
    }

    public format(message: string | null = null, {
        short = false, indent = "", colors = false, error = false, skipFilename = false,
    } = {}) {
        const rawLine = this.document.getLineContentAtCursor({ char: this.char, index: this.index, line: this.line })
        let tabOffset = 0
        while (tabOffset < rawLine.length) {
            const char = rawLine[tabOffset]

            if (char == " " || char == "\t") {
                tabOffset++
                continue
            }

            break
        }

        const line = rawLine.slice(tabOffset)
        const pointer = " ".repeat(this.char - tabOffset) + (this.length > 1 ? "~".repeat(this.length) : "^")
        const metadata = { offset: this.offset.toString(), length: this.length.toString() }
        const format = colors ? FORMAT[error ? "danger" : "primary"] : ((v: string, metadata?: any) => v)
        const formatLocation = colors ? FORMAT.primary : format

        return (
            indent + formatLocation((skipFilename ? "" : this.document.path + ":") + (this.line + 1) + ":" + (this.char + 1), metadata) +
            (message != null ? format(" - " + message) : "") +
            (short ? "" : "\n" + indent + line + "\n" + indent + format(pointer))
        )
    }

    public [LogMarker.CUSTOM](ctx: ObjectDescription.Context) {
        ctx.seen.delete(this)
        return LogMarker.rawText(this.format(null, { colors: true }), "white", { indent: true })
    }

    constructor(
        public readonly document: InputDocument,
        public readonly index: number,
        public readonly length: number,
    ) { }

    public static createSpecial(kind: string) {
        return new _SpecialPosition(kind) as Position
    }
}

export type _PositionFormattingOptions = Parameters<Position["format"]>[1]

/** A position outside any parsed documents, for use later in the compilation chain (i.e. language defined constructs).*/
class _SpecialPosition extends Position {
    public format(message?: string | null, { colors = false, indent = "", error = false }: _PositionFormattingOptions = {}): string {
        const format = colors ? FORMAT[error ? "danger" : "primary"] : ((v: string) => v)

        return (
            indent + (message != null ? format(message) : "")
        )
    }

    constructor(
        public readonly kind: string,
    ) {
        // @ts-ignore
        super()
    }
}
