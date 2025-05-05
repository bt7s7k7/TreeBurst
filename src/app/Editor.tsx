import { defineComponent } from "vue"
import { EditorView } from "../editor/EditorView"
import { TreeBurstEditorState } from "./TreeBurstEditorState"

export const Editor = (defineComponent({
    name: "Editor",
    setup(props, ctx) {
        const editorState = new TreeBurstEditorState()

        return () => (
            <EditorView state={editorState} root localStorageId="tree-burst:editor"></EditorView>
        )
    },
}))
