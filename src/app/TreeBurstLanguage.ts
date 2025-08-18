import CodeMirror, { Editor, Position } from "codemirror"
import { Annotation } from "codemirror/addon/lint/lint"
import { LanguageServiceHintResult, LanguageServiceState } from "../editor/LanguageServiceState"
import { EditorState } from "../editor/useEditorState"
import { DescriptionFormatter } from "../prettyPrint/DescriptionFormatter"
import { inspect } from "../prettyPrint/inspect"
import { Formatter } from "../textFormat/Formatter"
import { HTMLFormatter } from "../textFormatHTML/HTMLFormatter"
import { Diagnostic } from "../treeBurst/support/Diagnostic"
import { InputDocument } from "../treeBurst/support/InputDocument"
import { TreeBurstParser } from "../treeBurst/syntax/TreeBurstParser"
import "./style.scss"

const _DEFAULT_COLOR: DescriptionFormatter.FormatOptions["color"] = (text, color) => Formatter.adapt(text, color.custom ? "white" : color.name)
export function inspectToHtml(object: any) {
    return HTMLFormatter.render(inspect(object, { color: _DEFAULT_COLOR }))
}

export class TreeBurstLanguage extends LanguageServiceState {
    protected _ast: string | null = null
    protected _diagnostics: Diagnostic[] = []

    protected override _getHints(editor: Editor, position: Position, word: string): LanguageServiceHintResult {
        return { list: [] }
    }

    public override getAnnotations(code: string, editor: CodeMirror.Editor): Annotation[] {
        this.compile(code)

        return this._diagnostics.map(diagnostic => ({
            from: CodeMirror.Pos(diagnostic.position.line, diagnostic.position.char),
            to: CodeMirror.Pos(diagnostic.position.line, diagnostic.position.char + diagnostic.position.length),
            message: diagnostic.message,
        }))
    }

    public override getOutput(): EditorState.OutputTab[] {
        return [
            {
                name: "ast", label: "AST",
                content: this._ast ?? "",
            },
        ]
    }

    protected override _compile(code: string): void {
        this.ready = true

        const input = new InputDocument("anon", code)
        const parser = new TreeBurstParser(input)

        const result = parser.parse()
        this._ast = inspectToHtml(result)

        this._diagnostics = parser.diagnostics

        if (this._diagnostics.length > 0) {
            this.errors.push(...this._diagnostics.map(inspectToHtml))
        }
    }
}
