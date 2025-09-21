import { mkdir, rm, writeFile } from "node:fs/promises"
import { join } from "node:path"
import { Cli } from "./cli/Cli"
import { EMPTY_ARRAY } from "./comTypes/const"
import { DocumentationBuilder } from "./leafGen/DocumentationBuilder"
import { print, printError, printInfo } from "./leafGen/print"
import { Project } from "./leafGen/Project"
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
        name: "info", desc: "Prints all referenced symbols along with their information",
        options: {
            path: Type.string.as(Type.nullable),
        },
        async callback({ path }) {
            path ??= process.cwd()
            const project = await Project.load(path)

            const db = await project.parseFiles()

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
    .addOption({
        name: "build", desc: "Builds documentation",
        options: {
            path: Type.string.as(Type.nullable),
            markdown: Type.boolean.as(Type.nullable),
        },
        async callback({ path, markdown }) {
            path ??= process.cwd()
            const project = await Project.load(path)
            await project.fetchExternalReferences()

            const db = await project.parseFiles()
            const builder = new DocumentationBuilder(project, db)
            builder.sortSymbols()

            const docsPath = project.getDocsPath()
            await rm(docsPath, { recursive: true, force: true })
            await mkdir(docsPath, { recursive: true })

            await project.copyResourcesToDocsFolder()

            if (markdown) {
                for (const page of builder.buildMarkdown()) {
                    printInfo("Writing: " + page.filename)
                    await writeFile(join(docsPath, page.filename), page.build())
                }
            } else {
                for (const page of builder.buildHtml()) {
                    printInfo("Writing: " + page.filename)
                    await writeFile(join(docsPath, page.filename), page.content)
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
