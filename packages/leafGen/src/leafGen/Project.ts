import { readFileSync } from "node:fs"
import { cp, mkdir, readdir, readFile, writeFile } from "node:fs/promises"
import { dirname, extname, join, relative } from "node:path"
import { EMPTY_ARRAY } from "../comTypes/const"
import { ensureKey } from "../comTypes/util"
import { Struct } from "../struct/Struct"
import { Type } from "../struct/Type"
import { FileParser } from "./FileParser"
import { SymbolDatabase } from "./SymbolDatabase"
import { UserError } from "./UserError"
import { printInfo } from "./print"
import { matchesGlob } from "./support"

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
    resources: Type.object({
        path: Type.string,
        prefix: Type.string.as(Type.nullable, { skipNullSerialize: true }),
        include: Type.string.as(Type.array),
    }).as(Type.array).as(Type.nullable),
    emitSymbolDatabase: Type.boolean.as(Type.nullable, { skipNullSerialize: true }),
    externalReferences: Type.string.as(Type.array).as(Type.nullable),
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

    protected _externalReferences = new Map<string, string>()
    public async fetchExternalReferences() {
        if (this.externalReferences == null) return

        for (let reference of this.externalReferences) {
            if (!reference.endsWith("/")) reference += "/"
            printInfo("Fetching external reference: " + reference)

            const symbolFile = new URL("symbols.json", reference)
            const symbols: any = await fetch(symbolFile).then(v => v.json())
            for (const [symbol, path] of Object.entries(symbols)) {
                const symbolPage = new URL(path as string, reference)
                this._externalReferences.set(symbol, symbolPage.href)
            }
        }
    }

    public findExternalReference(name: string) {
        return this._externalReferences.get(name) ?? null
    }

    public getDocsPath() {
        return join(this.path, this.docsPath)
    }

    public async copyResourcesToDocsFolder() {
        if (this.resources == null) return

        const docsPath = this.getDocsPath()
        for (const { path, include, prefix } of this.resources) {
            const resourceFolderPath = join(this.path, path)
            for (const dirent of await readdir(resourceFolderPath, { withFileTypes: true, recursive: true })) {
                if (dirent.isFile() && include.some(v => matchesGlob(dirent.name, v))) {
                    const resourcePath = join(dirent.path ?? dirent.parentPath, dirent.name)

                    let relativePath = relative(resourceFolderPath, resourcePath)
                    if (prefix != null) {
                        relativePath = join(prefix, relativePath)
                    }

                    const outputPath = join(docsPath, relativePath)
                    await mkdir(dirname(outputPath), { recursive: true })
                    await cp(resourcePath, outputPath)
                }
            }
        }
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

        for (const [glob, insertPath] of this.inserts) {
            const insertExtension = extname(insertPath)
            if (insertExtension != extension) continue
            if (!matchesGlob(filename, glob)) continue

            const content = ensureKey(this._cachedInserts, insertPath, () => readFileSync(join(this.path, insertPath), "utf-8"))
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
