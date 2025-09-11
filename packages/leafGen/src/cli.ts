import { readFile } from "node:fs/promises"
import { relative } from "node:path"
import { Cli } from "./cli/Cli"
import { EMPTY_ARRAY } from "./comTypes/const"
import { FileParser } from "./leafGen/FileParser"
import { print, printError, printInfo } from "./leafGen/print"
import { Project } from "./leafGen/Project"
import { SymbolDatabase } from "./leafGen/SymbolDatabase"
import { UserError } from "./leafGen/UserError"
import { Type } from "./struct/Type"

export const cli = new Cli("leaf-gen")
    .addOption({
        name: "init", desc: "Creates a config file for the current project",
        options: {
            path: Type.string.as(Type.nullable),
        },
        async callback({ path }) {
            path ??= process.cwd()
            const project = Project.default()
            project.path = path
            await project.save()
        },
    })
    .addOption({
        name: "info", desc: "Verifies the project config and print all included files",
        options: {
            path: Type.string.as(Type.nullable),
        },
        async callback({ path }) {
            path ??= process.cwd()
            const project = await Project.load(path)

            for (const file of await project.getFileList()) {
                print(relative(project.path, file))
            }
        },
    })
    .addOption({
        name: "build", desc: "Scans project files and generates documentation",
        options: {
            path: Type.string.as(Type.nullable),
        },
        async callback({ path }) {
            path ??= process.cwd()
            const project = await Project.load(path)

            const db = new SymbolDatabase()
            const excludedFiles = new Set(project.excludedFiles ?? EMPTY_ARRAY)

            for (const file of await project.getFileList()) {
                const name = relative(project.path, file)
                if (excludedFiles.has(name)) continue
                printInfo("Processing: " + name)
                const fileContent = await readFile(file, "utf-8")
                const lines = fileContent.split("\n")
                const parser = new FileParser(file, lines, project, db)
                parser.parse()
            }

            db.postProcessSymbols()

            const symbols = Array.from(db.getSymbolNames())
            symbols.sort()

            for (const symbolName of symbols) {
                const symbol = db.getSymbol(symbolName)
                if (symbol.isEntry) {
                    printInfo(`\x1b[92m<Entry>\x1b[96m ${symbolName}`)
                } else {
                    printInfo(symbolName)
                }

                if (symbol.prototype) {
                    print(`    Prototype: ${symbol.prototype.name}`)
                }

                if (symbol.value) {
                    print(`    Value: ${symbol.value.name}`)
                }

                if (symbol.isFunction) {
                    print("    [Function]")
                    for (const overload of symbol.overloads ?? EMPTY_ARRAY) {
                        const formattedParameters = overload.parameters.map((v, i) => `${v}: ${overload.types?.at(i)?.name ?? "any"}`)
                        if (overload.isVariadic) {
                            formattedParameters.push("...")
                        }
                        print(`    Overload: (${formattedParameters.join(", ")})`)
                    }
                }

                for (const line of symbol.summary) {
                    print(`    \x1b[32m${line}\x1b[0m`)
                }

                for (const site of symbol.sites) {
                    print(`    \x1b[2m${site}\x1b[0m`)
                }
            }
        },
    })

export async function executeCommand(args: string[]) {
    try {
        await cli.execute(args)
    } catch (err) {
        if (err instanceof UserError) {
            printError(err.message)
            process.exit(1)
        }

        throw err
    }
}
