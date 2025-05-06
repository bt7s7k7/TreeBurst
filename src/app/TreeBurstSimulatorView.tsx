import { mdiPlay } from "@mdi/js"
import { defineComponent, PropType } from "vue"
import { unreachable } from "../comTypes/util"
import { Button } from "../vue3gui/Button"
import { TreeBurstEditorState } from "./TreeBurstEditorState"
import { TreeBurstSimulator } from "./TreeBurstSimulator"

export const TreeBurstSimulatorView = (defineComponent({
    name: "TreeBurstSimulatorView",
    props: {
        state: { type: Object as PropType<TreeBurstEditorState>, required: true },
    },
    setup(props, ctx) {
        const simulator = new TreeBurstSimulator(props.state.result ?? unreachable())

        function execute() {
            simulator.run()
        }

        return () => (
            <div class="flex column">
                <div class="border-bottom flex row gap-2 p-2">
                    <Button icon={mdiPlay} variant="success" label="Execute" disabled={simulator.messages.length > 0} onClick={execute} />
                </div>
                {simulator.messages.map(message => (
                    <pre class="p-2 m-0 border-bottom" innerHTML={message} />
                ))}
            </div>
        )
    },
}))
