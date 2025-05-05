import { Readwrite } from "../comTypes/types"
import { LogMarker } from "../prettyPrint/ObjectDescription"
import { Struct } from "../struct/Struct"
import { Type } from "../struct/Type"
import { UnionType } from "../struct/UnionType"

export class TreeNode extends Struct.define("TreeNode", class {
    public readonly value = Struct.field(UnionType.create(Type.string, Type.number).as(Type.nullable))
    public readonly children: readonly TreeNode[] | null = Struct.field(TreeNode.ref().as(Type.array).as(Type.nullable))
    public readonly entries: ReadonlyMap<string, TreeNode> | null = Struct.field(TreeNode.ref().as(Type.map).as(Type.nullable))
}) {
    public addChild(child: TreeNode) {
        if (this.children == null) {
            (this as Readwrite<this>).children = []
        }

        (this.children as TreeNode[]).push(child)
    }

    public setEntry(name: string | number, node: TreeNode) {
        if (typeof name == "number") {
            if (this.children == null) {
                (this as Readwrite<this>).children = []
            }

            while (this.children!.length <= name) {
                (this.children as TreeNode[]).push(TreeNode.default())
            }

            (this.children as TreeNode[])[name] = node

            return
        }

        if (this.entries == null) {
            (this as Readwrite<this>).entries = new Map()
        }

        (this.entries as Map<string, TreeNode>).set(name, node)
    }

    public deleteEntry(name: string | number) {
        if (typeof name == "number") {
            if (this.children && this.children.length > name) {
                (this.children as TreeNode[]).splice(name, 1)
                if (this.children.length == 0) {
                    (this as Readwrite<this>).children = null
                }
                return true
            }

            return false
        }

        if (this.entries == null) {
            return false
        }

        const result = (this.entries as Map<string, TreeNode>).delete(name)
        if (result && this.entries.size == 0) {
            (this as Readwrite<this>).entries = null
        }
        return result
    }

    public getEntry(name: string) {
        return this.entries?.get(name) ?? null
    }

    public [LogMarker.CUSTOM]() {
        if (this.children == null && this.entries == null) return this.value
        if (this.value == null && this.entries == null) return this.children
        if (this.value == null && this.children == null) return this.entries

        return this
    }
}
