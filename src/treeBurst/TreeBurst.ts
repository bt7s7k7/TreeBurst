import { EMPTY_ARRAY } from "../comTypes/const"
import { unreachable } from "../comTypes/util"
import { FLAG_CONST, TreeNode } from "./TreeNode"

export class Path {
    public get isRoot() { return !this.isRelative && this.segments.length == 0 }
    public get basename() { return this.segments.length > 0 ? this.segments[this.segments.length - 1] : unreachable() }
    public get isEmpty() { return this.segments.length == 0 }

    public getDirname() {
        return new Path(this.isRelative, this.segments.slice(0, -1))
    }

    constructor(
        public readonly isRelative: boolean,
        public readonly segments: readonly string[],
    ) { }

    public static parse(source: string) {
        let relative = true
        if (source[0] == "/") {
            relative = false
            source = source.slice(1)
        }

        return new Path(relative, source.split("/"))
    }

    public static ROOT = new Path(false, EMPTY_ARRAY)
}

export class TreeBurst {
    public readonly stack
    public readonly frames
    public currentFrame: TreeNode | null = null
    public currentCwd = Path.ROOT

    public read(path: Path, cwd: Path): TreeNode | null {
        if (path.isEmpty) {
            if (cwd.isRoot) return this.root
            return this.read(cwd, Path.ROOT)
        }

        let target = path.isRelative ? (
            cwd.isRoot ? this.root : this.read(cwd, Path.ROOT)
        ) : (
            this.root
        )

        if (target == null) return null

        for (const segment of path.segments) {
            target = target.getEntry(segment)
            if (target == null) return null
        }

        return target
    }

    public write(path: Path, cwd: Path, value: string | number | null): boolean {
        if (path.isEmpty) return false

        const target = this.read(path.getDirname(), cwd)
        if (target == null) return false
        const basename=  path.basename
        const existing = target.getEntry(basename)
        if (existing) {
            existing.setValue(value)
            return true
        } else {
            target.setEntry(basename, )
        }
    }

    public execute(instruction: TreeNode) {

    }

    constructor(
        public readonly root: TreeNode,
    ) {
        this.stack = root.ensureEntry(".stack")
        this.stack.flags |= FLAG_CONST
        this.frames = root.ensureEntry(".frames")
        this.frames.flags |= FLAG_CONST
    }
}
