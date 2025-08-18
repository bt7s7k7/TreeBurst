import { defineComponent } from "vue"
import { EditorView } from "../editor/EditorView"
import { TreeBurstLanguage } from "./TreeBurstLanguage"

export const EditorPage = (defineComponent({
    name: "EditorPage",
    setup(props, ctx) {
        const state = new TreeBurstLanguage()

        return () => (
            <EditorView state={state} root localStorageId="treeBurst:editor" />
        )
    },
}))
