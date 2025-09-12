import { readFileSync } from "node:fs"
import { readdir, readFile, writeFile } from "node:fs/promises"
import { extname, join, relative } from "node:path"
import { EMPTY_ARRAY } from "../comTypes/const"
import { ensureKey } from "../comTypes/util"
import { Struct } from "../struct/Struct"
import { Type } from "../struct/Type"
import { FileParser } from "./FileParser"
import { SymbolDatabase } from "./SymbolDatabase"
import { UserError } from "./UserError"
import { printInfo } from "./print"

export class SymbolFactoryDefinition extends Struct.define("SymbolFactoryDefinition", {
    pattern: Type.string,
    template: Type.string,
    owner: Type.string,
}) {
    protected _regexp: RegExp | null = null
    public getRegExp() {
        return this._regexp ??= new RegExp(this.pattern)
    }
}

export class Project extends Struct.define("Project", {
    sourceRoot: Type.string,
    docsPath: Type.string,
    title: Type.string.as(Type.withDefault, () => "Project"),
    excludedFiles: Type.string.as(Type.array).as(Type.nullable).as(Type.withDefault, () => []),
    symbolFactories: SymbolFactoryDefinition.ref().as(Type.array).as(Type.nullable).as(Type.withDefault, () => []),
    inserts: Type.string.as(Type.map).as(Type.nullable).as(Type.withDefault, () => new Map() as never),
}) {
    public path: string = null!

    public select() {
        Project.instance = this
    }

    public async save() {
        await writeFile(join(this.path, "leaf-gen.json"), JSON.stringify(this.serialize(), null, 4))
    }

    public async getFileList() {
        const files: string[] = []

        for await (const dirent of await readdir(join(this.path, this.sourceRoot), { withFileTypes: true, recursive: true })) {
            if (dirent.isFile() && dirent.name.endsWith(".java")) {
                files.push(join(dirent.path ?? dirent.parentPath, dirent.name))
            }
        }

        return files
    }

    public async parseFiles() {
        const db = new SymbolDatabase()
        const excludedFiles = new Set(this.excludedFiles ?? EMPTY_ARRAY)

        for (const file of await this.getFileList()) {
            const name = relative(this.path, file)
            if (excludedFiles.has(name)) continue
            printInfo("Processing: " + name)
            const fileContent = await readFile(file, "utf-8")
            const lines = fileContent.split("\n")
            const parser = new FileParser(file, lines, this, db)
            parser.parse()
        }

        db.postProcessSymbols()
        return db
    }

    protected _cachedInserts = new Map<string, string>()
    public getInsertsForFilename(filename: string, extension: string) {
        if (this.inserts == null) return EMPTY_ARRAY as never

        const result: string[] = []

        for (const [key, value] of this.inserts) {
            const insertExtension = extname(value)
            if (insertExtension != extension) continue

            if (key.startsWith("*") && key.endsWith("*")) {
                if (!filename.includes(key)) continue
            } else if (key.startsWith("*")) {
                if (!filename.endsWith(key.slice(1))) continue
            } else if (key.endsWith("*")) {
                if (!filename.startsWith(key.slice(0, -1))) continue
            } else {
                if (filename != key) continue
            }

            const content = ensureKey(this._cachedInserts, value, () => readFileSync(join(this.path, value), "utf-8"))
            result.push(content)
        }

        return result as readonly string[]
    }

    public static instance: Project = null!
    public static async load(path: string) {
        const configFile = join(path, "leaf-gen.json")
        const data = await readFile(configFile, "utf-8").catch(() => null)
        if (data == null) {
            throw new UserError("Missing config file")
        }

        const project = Project.deserialize(JSON.parse(data))
        project.path = path
        project.select()
        return project
    }
}
