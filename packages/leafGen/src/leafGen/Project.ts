import { readdir, readFile, writeFile } from "node:fs/promises"
import { join, relative } from "node:path"
import { EMPTY_ARRAY } from "../comTypes/const"
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
    excludedFiles: Type.string.as(Type.array).as(Type.nullable).as(Type.withDefault, () => []),
    symbolFactories: SymbolFactoryDefinition.ref().as(Type.array).as(Type.nullable).as(Type.withDefault, () => []),
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
