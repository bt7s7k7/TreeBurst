import { Readwrite } from "../comTypes/types"
import { cloneWithout } from "../comTypes/util"
import { LogMarker } from "../prettyPrint/ObjectDescription"
import { Struct } from "../struct/Struct"
import { Type } from "../struct/Type"
import { UnionType } from "../struct/UnionType"

export const FLAG_CONST = 0b1

export class TreeNode extends Struct.define("TreeNode", class {
    public flags = Struct.field(Type.number)
    public readonly value = Struct.field(UnionType.create(Type.string, Type.number).as(Type.nullable))
    public readonly children: readonly TreeNode[] | null = Struct.field(TreeNode.ref().as(Type.array).as(Type.nullable))
    public readonly entries: ReadonlyMap<string, TreeNode> | null = Struct.field(TreeNode.ref().as(Type.map).as(Type.nullable))
}) {
    public get revision() { return this.flags >> 16 }

    protected _incrementRevision() {
        const flags = this.flags & 0x0000ffff
        let revision = this.flags >> 16
        revision++
        if (revision > 0xffff) {
            revision = 0
        }

        this.flags = flags | (revision << 16)
    }

    public setValue(value: string | number | null) {
        if (this.value == value) return
        (this as Readwrite<this>).value = value
        this._incrementRevision()
    }

    public addChild(child: TreeNode) {
        if (this.children == null) {
            (this as Readwrite<this>).children = []
        }

        (this.children as TreeNode[]).push(child)
        this._incrementRevision()
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
            this._incrementRevision()

            return node
        }

        if (this.entries == null) {
            (this as Readwrite<this>).entries = new Map()
        }

        (this.entries as Map<string, TreeNode>).set(name, node)
        this._incrementRevision()
        return node
    }

    public deleteEntry(name: string | number) {
        if (typeof name == "number") {
            if (this.children && this.children.length > name) {
                (this.children as TreeNode[]).splice(name, 1)
                this._incrementRevision()

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
        if (result) {
            this._incrementRevision()

            if (this.entries.size == 0) {
                (this as Readwrite<this>).entries = null
            }
        }

        return result
    }

    public getEntry(name: string) {
        return this.entries?.get(name) ?? null
    }

    public ensureEntry(name: string) {
        return this.entries?.get(name) ?? this.setEntry(name, TreeNode.default())
    }

    public [LogMarker.CUSTOM]() {
        if (this.children == null && this.entries == null) return this.value
        if (this.value == null && this.entries == null) return this.children
        if (this.value == null && this.children == null) return this.entries

        return "flags" in this ? cloneWithout(this, "flags") : this
    }
}
