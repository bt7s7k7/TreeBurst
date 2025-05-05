import { countOccurrences, findNthOccurrence } from "../comTypes/util"
import { Position } from "./Position"

/** Representation of a parsed document. */

export class InputDocument {
    public getCursorAtIndex(index: number): InputDocument.Cursor {
        let lineStart = this.content.lastIndexOf("\n", this.content[index] == "\n" ? index - 1 : index)
        if (lineStart == -1) lineStart = 0
        else if (lineStart != 0) lineStart++

        const char = index - lineStart
        const line = countOccurrences(this.content, "\n", 0, lineStart) + this.lineOffset
        return { line, char, index }
    }

    public getLineContentAtCursor(cursor: InputDocument.Cursor) {
        const lineStart = cursor.index - cursor.char
        let lineEnd = this.content.indexOf("\n", cursor.index)
        if (lineEnd == -1) lineEnd = this.content.length
        return this.content.slice(lineStart, lineEnd)
    }

    public getCursorByLine(line: number, char: number): InputDocument.Cursor {
        const content = this.content
        let lineStart = findNthOccurrence(content, "\n", line - this.lineOffset)
        if (lineStart == -1) lineStart = 0
        else lineStart++

        let lineEnd = content.indexOf("\n", lineStart)
        if (lineEnd == -1) lineEnd = content.length
        const index = lineStart + char

        return { line, char, index }
    }

    /** Returns a position at the end of the document */
    public getEOF() {
        return new Position(this, this.content.length, 1)
    }

    /** Returns a position spanning the whole document */
    public getFullRange() {
        return new Position(this, 0, this.content.length)
    }

    constructor(
        public readonly path: string,
        public readonly content: string,
        public readonly lineOffset = 0,
    ) { }
}

export namespace InputDocument {
    export interface Cursor {
        line: number
        char: number
        index: number
    }
}
