import { markRaw, reactive } from "vue"
import { inspectToHtml } from "../primitiveParser/interop"
import { TreeBurst } from "../treeBurst/TreeBurst"
import { TreeNode } from "../treeBurst/TreeNode"

export class TreeBurstSimulator {
    public readonly messages: string[] = reactive([])

    public run() {
        const engine = new TreeBurst(this.root, msg => this.messages.push(inspectToHtml(msg)))
        // @ts-ignore
        window.engine = engine

        engine.run()

        this.messages.push(inspectToHtml(engine.result))
    }

    constructor(
        public readonly root: TreeNode,
    ) {
        markRaw(this)
    }
}
