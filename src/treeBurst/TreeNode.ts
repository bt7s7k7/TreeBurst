import { Readwrite } from "../comTypes/types"
import { cloneWithout } from "../comTypes/util"
import { LogMarker } from "../prettyPrint/ObjectDescription"
import { InputDocument } from "../primitiveParser/InputDocument"
import { Position } from "../primitiveParser/Position"
import { Struct } from "../struct/Struct"
import { Type } from "../struct/Type"
import { UnionType } from "../struct/UnionType"
import { Path } from "./TreeBurst"

export const TreeNodeValue_t = UnionType.create(Type.string, Type.number).as(Type.nullable)
export type TreeNodeValue = Type.Extract<typeof TreeNodeValue_t>

export class TreeNode extends Struct.define("TreeNode", class {
    public flags = Struct.field(Type.number)
    public readonly value = Struct.field(TreeNodeValue_t)
    public readonly children: readonly TreeNode[] | null = Struct.field(TreeNode.ref().as(Type.array).as(Type.nullable))
    public readonly entries: ReadonlyMap<string, TreeNode> | null = Struct.field(TreeNode.ref().as(Type.map).as(Type.nullable))
}) {
    public get revision() { return this.flags >> 16 }

    public parent: TreeNode | null = null
    public name: string | number | null = null
    public position: Position | null = null

    public getPath() {
        const segments: (string | number)[] = []

        for (let i: TreeNode | null = this; i != null; i = i.parent) {
            if (i.name) segments.unshift(i.name)
        }

        return new Path(false, segments)
    }

    public getPosition() {
        if (this.position) return this.position
        return new Position(new InputDocument(this.getPath().toString(), ""), 0, 0)
    }

    protected _postDeserialize() {
        if (this.children != null) {
            for (let i = 0; i < this.children.length; i++) {
                this.children[i].parent = this
                this.children[i].name = i
            }
        }

        if (this.entries != null) {
            for (const [name, node] of this.entries) {
                node.parent = this
                node.name = name
            }
        }
    }

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

        child.parent = this
        child.name = this.children!.length - 1

        this._incrementRevision()
    }

    public setEntry(name: string | number, node: TreeNode) {
        node.parent = this
        node.name = name

        if (typeof name == "number") {
            if (this.children == null) {
                (this as Readwrite<this>).children = []
            }

            while (this.children!.length <= name) {
                const filler = TreeNode.default();
                (this.children as TreeNode[]).push(filler)
                filler.parent = this
                filler.name = this.children!.length - 1
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
                const removedChild = this.children![name]
                removedChild.parent = null
                removedChild.name = null;

                (this.children as TreeNode[]).splice(name, 1)

                for (let i = name; i < this.children.length; i++) {
                    this.children[i].name = i
                }

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
        const removedChild = this.entries.get(name)
        if (removedChild) {
            removedChild.parent = null
            removedChild.name = null;
            (this.entries as Map<string, TreeNode>).delete(name)

            this._incrementRevision()

            if (this.entries.size == 0) {
                (this as Readwrite<this>).entries = null
            }

            return true
        }

        return false
    }

    public getEntry(name: string | number) {
        if (typeof name == "number") {
            if (this.children && this.children.length > name) {
                return this.children[name]
            }

            return null
        }

        return this.entries?.get(name) ?? null
    }

    public ensureEntry(name: string | number) {
        return this.getEntry(name) ?? this.setEntry(name, TreeNode.default())
    }

    public [LogMarker.CUSTOM]() {
        if (this.children == null && this.entries == null) return this.value
        if (this.value == null && this.entries == null) return this.children
        if (this.value == null && this.children == null) return this.entries

        return "flags" in this ? cloneWithout(this, "flags", "parent", "name") : this
    }

    public static withValue(value: TreeNodeValue) {
        return new TreeNode({
            flags: 0,
            value,
            children: null,
            entries: null,
        })
    }
}
