import { EditorState } from "../editor/useEditorState"
import { Diagnostic } from "../primitiveParser/Diagnostic"
import { InputDocument } from "../primitiveParser/InputDocument"
import { diagnosticToHtml, inspectToHtml } from "../primitiveParser/interop"
import { TreeBurstParser } from "../treeBurst/TreeBurstParser"
import { TreeNode } from "../treeBurst/TreeNode"

export class TreeBurstEditorState extends EditorState {
    public result: TreeNode | null = null
    public _load: string | null = null
    public diagnostics: Diagnostic[] = []

    public getOutput(): EditorState.OutputTab[] {
        return [
            {
                name: "ast", label: "AST",
                content: this._load ?? "",
            },
        ]
    }

    protected _compile(code: string): void {
        this.ready = true
        this.result = null
        this._load = null

        const input = new InputDocument("anon", code)
        const parser = new TreeBurstParser(input)

        this.result = parser.parse()
        this.diagnostics = parser.diagnostics

        this._load = inspectToHtml(this.result)

        if (parser.diagnostics.length > 0) {
            this.errors.push(...parser.diagnostics.map(diagnosticToHtml))
        }
    }
}
