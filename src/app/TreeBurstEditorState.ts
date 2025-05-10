import CodeMirror from "codemirror"
import { h } from "vue"
import { EditorState } from "../editor/useEditorState"
import { Diagnostic } from "../primitiveParser/Diagnostic"
import { InputDocument } from "../primitiveParser/InputDocument"
import { diagnosticToHtml, inspectToHtml } from "../primitiveParser/interop"
import { TreeBurstParser } from "../treeBurst/TreeBurstParser"
import { TreeNode } from "../treeBurst/TreeNode"
import { TREE_BURST_MODE } from "./TreeBurstMode"
import { TreeBurstSimulatorView } from "./TreeBurstSimulatorView"

CodeMirror.defineSimpleMode("tree-burst", TREE_BURST_MODE)

export class TreeBurstEditorState extends EditorState {
    public result: TreeNode | null = null
    public _load: string | null = null
    public diagnostics: Diagnostic[] = []
    public iteration = 0

    public getOutput(): EditorState.OutputTab[] {
        return [
            {
                name: "ast", label: "AST",
                content: this._load ?? "",
            },
            {
                name: "run", label: "Run",
                content: () => this.result ? h(TreeBurstSimulatorView, { state: this, key: this.iteration }) : null,
            },
        ]
    }

    protected _compile(code: string): void {
        this.ready = true
        this.result = null
        this._load = null
        this.iteration++

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
