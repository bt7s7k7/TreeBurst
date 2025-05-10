import { EMPTY_ARRAY } from "../comTypes/const"
import { unreachable } from "../comTypes/util"
import { Diagnostic } from "../primitiveParser/Diagnostic"
import { Position } from "../primitiveParser/Position"
import { TreeNode, TreeNodeRef, TreeNodeValue } from "./TreeNode"

function _arrayEquals<T>(a: readonly T[], b: readonly T[]) {
    if (a.length != b.length) return false

    for (let i = 0; i < a.length; i++) {
        if (a[i] != b[i]) return false
    }

    return true
}

export class Path {
    public get isRoot() { return !this.isRelative && this.segments.length == 0 }
    public get basename() { return this.segments.length > 0 ? this.segments[this.segments.length - 1] : unreachable() }
    public get isEmpty() { return this.segments.length == 0 }

    public getDirname() {
        if (this.isRelative && (this.segments.length == 1 || this.segments.length == 0)) return Path.EMPTY
        if (!this.isRelative && (this.segments.length == 1 || this.segments.length == 0)) return Path.ROOT
        return new Path(this.isRelative, this.segments.slice(0, -1))
    }

    public equals(other: Path) {
        return this == other || (
            this.isRelative == other.isRelative &&
            _arrayEquals(this.segments, other.segments)
        )
    }

    public toString() {
        return (this.isRelative ? "./" : "/") + this.segments.join("/")
    }

    constructor(
        public readonly isRelative: boolean,
        public readonly segments: readonly (string | number)[],
    ) { }

    public static parse(source: string | number) {
        let relative = true
        if (typeof source == "string" && source[0] == "/") {
            relative = false
            source = source.slice(1)
        }

        let segments: (string | number)[] | null = null

        for (const segment of typeof source == "number" ? [source] : source.split("/")) {
            if (segment == "") continue
            if (segment == ".") continue
            if (segment == "..") {
                if (segments != null && segments.length != 0) {
                    segments.pop()
                    continue
                }
            }

            segments ??= []
            if (typeof segment == "string" && segment.match(/^\d+$/)) {
                segments.push(+segment)
            } else {
                segments.push(segment)
            }
        }

        return new Path(relative, segments ?? EMPTY_ARRAY)
    }

    public static ROOT = new Path(false, EMPTY_ARRAY)
    public static EMPTY = new Path(true, EMPTY_ARRAY)
}

export class ExecutionFrame {
    public target: TreeNode | IntrinsicInstruction | null = null
    public targetScope: TreeNode | null = null
    public targetPath: Path | null = null

    public getPositionAtIp() {
        if (!(this.thunk instanceof TreeNode)) unreachable()
        return this.thunk.getEntry(this.ip - 1)!.getPosition()
    }

    constructor(
        public readonly thunk: TreeNode | IntrinsicInstruction,
        public readonly scope: TreeNode,
        public ip: number,
        public cwd: Path,
        public readonly isFunctionRoot: boolean,
    ) { }
}

export class IntrinsicInstruction {

    public getPath() {
        return Path.ROOT
    }

    constructor(
        public readonly name: string,
        public readonly params: string[],
        public readonly callback: (ctx: TreeBurst, frame: ExecutionFrame) => boolean,
    ) { }
}

export class TreeBurst {
    public result: TreeNodeValue = null

    public stack = [
        new ExecutionFrame(this.root, TreeNode.default(), 0, Path.ROOT, true),
    ]

    public get currentFrame() { return this.stack.length == 0 ? null : this.stack[this.stack.length - 1] }

    public readonly intrinsics = new Map<TreeNodeValue, IntrinsicInstruction>()

    public read(path: Path, cwd: Path | TreeNode): TreeNode | null {
        if (path.isEmpty) {
            if (cwd instanceof TreeNode) return cwd

            if (cwd.isRoot) return this.root
            return this.read(cwd, Path.ROOT)
        }

        let target = path.isRelative ? (
            cwd instanceof TreeNode ? (
                cwd
            ) : (
                cwd.isRoot ? this.root : this.read(cwd, Path.ROOT)
            )
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

    public write(path: Path, cwd: Path | TreeNode, value: TreeNodeValue): boolean {
        if (path.isEmpty) return false

        const target = this.read(path.getDirname(), cwd)
        if (target == null) return false
        const basename = path.basename
        const existing = target.getEntry(basename)

        if (existing) {
            existing.setValue(value)
        } else {
            target.setEntry(basename, TreeNode.withValue(value))
        }

        return true
    }

    public emplaceFrame(thunk: ExecutionFrame["thunk"], scope: TreeNode, cwd: Path, isFunctionRoot: boolean) {
        this.stack.push(new ExecutionFrame(thunk, scope, 0, cwd, isFunctionRoot))
    }

    public executeThunk(thunk: TreeNodeValue, owner: ExecutionFrame) {
        if (thunk == null) {
            return true
        }

        if (typeof thunk == "object") {
            this.emplaceFrame(thunk.target, owner.scope, owner.cwd, false)
        } else {
            const targetValue = this.read(Path.parse(thunk), owner.cwd)
            if (targetValue == null) {
                this.raiseException("Cannot find the specified path", owner.getPositionAtIp())
                return false
            }
            this.emplaceFrame(targetValue, owner.scope, owner.cwd, false)
        }

        return true
    }

    public finalizeExecutionFrame() {
        const executionFrame = this.stack.pop() ?? unreachable()

        if (executionFrame.target) {
            this.emplaceFrame(
                executionFrame.target,
                executionFrame.targetScope ?? unreachable(),
                executionFrame.targetPath ?? unreachable(),
                true,
            )
        } else {
            if (!this.setArgument(executionFrame.scope.getEntry(".return")?.value ?? null)) {
                return false
            }
        }

        return true
    }

    public prepareFunctionCall(instructionName: NonNullable<TreeNodeValue>, cwd: Path, parent: TreeNode) {
        const intrinsic = this.intrinsics.get(instructionName)
        let instruction
        let path
        if (intrinsic != null) {
            instruction = intrinsic
            path = intrinsic.getPath()
        } else if (typeof instructionName == "object") {
            instruction = instructionName.target
            path = instruction.getPath()
        } else {
            path = Path.parse(instructionName)
            instruction = this.read(path, cwd)
            if (instruction == null) {
                this.raiseException(`Cannot find function "${instructionName}"`, parent.getPosition())
            }
        }

        return { instruction, path }
    }

    public step() {
        const executionFrame = this.currentFrame
        if (executionFrame == null) return false

        if (executionFrame.thunk instanceof IntrinsicInstruction) {
            const lastStackLength = this.stack.length
            if (executionFrame.ip == 0) {
                const result = executionFrame.thunk.callback(this, executionFrame)
                if (this.stack.length > lastStackLength) {
                    executionFrame.ip++
                    return result
                } else {
                    return this.finalizeExecutionFrame() && result
                }
            } else {
                return this.finalizeExecutionFrame()
            }
        }

        const instructionNode = executionFrame.thunk.getEntry(executionFrame.ip)
        if (instructionNode == null) {
            return this.finalizeExecutionFrame()
        }

        const instructionName = instructionNode.value
        if (instructionName == null) {
            if (instructionNode.children && instructionNode.children.length > 1) {
                this.print(new Diagnostic("Too many values in constant", instructionNode.getPosition()))
                return false
            }
            const constant = instructionNode.getEntry(0)
            executionFrame.ip++
            return this.setArgument(constant?.value ?? null)
        }

        if (instructionName == ".x") {
            executionFrame.ip++
            return this.setArgument(new TreeNodeRef({ target: instructionNode }))
        }

        const { instruction, path } = this.prepareFunctionCall(instructionName, executionFrame.cwd, instructionNode)
        if (instruction == null) return false

        const instructionFrame = new ExecutionFrame(
            instructionNode,
            executionFrame.scope,
            0,
            executionFrame.cwd,
            false,
        )

        instructionFrame.target = instruction
        instructionFrame.targetScope = TreeNode.default()
        instructionFrame.targetPath = path
        this.stack.push(instructionFrame)
        executionFrame.ip++
        return true
    }

    public run() {
        while (this.step()) {
            // Run
        }
    }

    public getParameters(target: TreeNode | IntrinsicInstruction) {
        if (target instanceof IntrinsicInstruction) {
            return target.params
        }

        const paramsNode = target.getEntry(".params")
        const params: (string | number)[] = []
        if (paramsNode == null || paramsNode.children == null || paramsNode.children.length == 0) {
            return params
        }

        let index = 0
        for (const param of paramsNode.children) {
            if (typeof param.value == "object") {
                this.raiseException("Unexpected reference in parameter list", param.getPosition())
                return null
            }

            params.push(param.value ?? index)
            index++
        }

        return params
    }

    public setArgument(value: TreeNodeValue) {
        if (this.currentFrame == null) {
            this.result = value
            return true
        }

        const executionFrame = this.currentFrame
        if (executionFrame.target) {
            const params = this.getParameters(executionFrame.target)
            if (params == null) {
                return false
            }

            const index = executionFrame.ip - 1
            if (index < 0) unreachable()
            if (index >= params.length) {
                executionFrame.targetScope!.ensureEntry(index).setValue(value)
            } else {
                executionFrame.targetScope!.ensureEntry(params[index]).setValue(value)
            }
        } else {
            executionFrame.scope.ensureEntry(".return").setValue(value)
        }

        return true
    }

    public addIntrinsicInstruction(instruction: IntrinsicInstruction) {
        this.intrinsics.has(instruction.name) && unreachable()
        this.intrinsics.set(instruction.name, instruction)
    }

    public getOwner() {
        return this.stack[this.stack.length - 2]
    }

    public raiseException(message: string, position: Position) {
        this.print(new Diagnostic(message, position))
    }

    constructor(
        public readonly root: TreeNode,
        public readonly print: (msg: any) => void,
    ) {
        this.addIntrinsicInstruction(new IntrinsicInstruction(".add", [], (ctx, frame) => {
            let result = 0

            if (frame.scope.children) {
                for (const child of frame.scope.children) {
                    result += child.value == null ? 0 : +child.value
                }
            }

            frame.scope.ensureEntry(".return").setValue(result)
            return true
        }))

        this.addIntrinsicInstruction(new IntrinsicInstruction(".str", [], (ctx, frame) => {
            let result = ""

            if (frame.scope.children) {
                for (const child of frame.scope.children) {
                    result += String(child.value)
                }
            }

            frame.scope.ensureEntry(".return").setValue(result)

            return true
        }))

        this.addIntrinsicInstruction(new IntrinsicInstruction(".st", ["name", "value"], (ctx, frame) => {
            const owner = ctx.getOwner()

            const name = frame.scope.getEntry("name")?.value
            if (name == null) {
                ctx.raiseException("Missing variable name", owner.getPositionAtIp())
                return false
            }

            if (typeof name == "object") {
                ctx.raiseException("Variable name cannot be a reference", owner.getPositionAtIp())
                return false
            }

            const value = frame.scope.getEntry("value")?.value
            owner.scope.ensureEntry(name).setValue(value ?? null)

            return true
        }))

        this.addIntrinsicInstruction(new IntrinsicInstruction(".ld", ["name"], (ctx, frame) => {
            const owner = ctx.getOwner()

            const name = frame.scope.getEntry("name")?.value
            if (name == null) {
                ctx.raiseException("Missing variable name", owner.getPositionAtIp())
                return false
            }

            if (typeof name == "object") {
                ctx.raiseException("Variable name cannot be a reference", owner.getPositionAtIp())
                return false
            }

            frame.scope.ensureEntry(".return").setValue(owner.scope.getEntry(name)?.value ?? null)

            return true
        }))

        this.addIntrinsicInstruction(new IntrinsicInstruction(".out", [], (ctx, frame) => {
            if (frame.scope.children == null || frame.scope.children.length == 0) {
                ctx.print(null)
            } else if (frame.scope.children.length == 1) {
                ctx.print(frame.scope.children[0].value)
            } else {
                ctx.print(frame.scope.children.map(v => v.value))
            }

            return true
        }))

        this.addIntrinsicInstruction(new IntrinsicInstruction(".do", ["then"], (ctx, frame) => {
            const owner = ctx.getOwner()

            const then = frame.scope.getEntry("then")
            if (then == null) {
                ctx.raiseException("Missing then block", owner.getPositionAtIp())
                return false
            }

            return ctx.executeThunk(then.value, owner)
        }))

        this.addIntrinsicInstruction(new IntrinsicInstruction(".if", ["predicate", "then", "else"], (ctx, frame) => {
            const owner = ctx.getOwner()

            const predicate = frame.scope.getEntry("predicate")
            if (predicate == null) {
                ctx.raiseException("Missing predicate", owner.getPositionAtIp())
                return false
            }

            const then = frame.scope.getEntry("then")
            if (then == null) {
                ctx.raiseException("Missing then block", owner.getPositionAtIp())
                return false
            }

            const predicateValue = predicate.value
            if (predicateValue) {
                return ctx.executeThunk(then.value, owner)
            } else {
                const elseEntry = frame.scope.getEntry("else")
                if (elseEntry) {
                    return ctx.executeThunk(elseEntry.value, owner)
                }
            }

            return true
        }))

        this.addIntrinsicInstruction(new IntrinsicInstruction(".while", ["predicate", "then"], (ctx, frame) => {
            const owner = ctx.getOwner()

            const predicate = frame.scope.getEntry("predicate")
            if (predicate == null) {
                ctx.raiseException("Missing predicate", owner.getPositionAtIp())
                return false
            }

            const then = frame.scope.getEntry("then")
            if (then == null) {
                ctx.raiseException("Missing then block", owner.getPositionAtIp())
                return false
            }

            const predicateValue = predicate.value
            if (predicateValue) {
                owner.ip--
                return ctx.executeThunk(then.value, owner)
            }

            return true
        }))
    }
}
