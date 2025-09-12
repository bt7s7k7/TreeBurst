import { relative } from "node:path"
import templateHtml from "../../template.html"
import { ensureKey, isWord, iteratorNth } from "../comTypes/util"
import { MmlHtmlRenderer } from "../miniML/MmlHtmlRenderer"
import { MmlParser } from "../miniML/MmlParser"
import { Project } from "./Project"
import { SymbolDatabase } from "./SymbolDatabase"
import { SymbolHandle } from "./SymbolHandle"

export class Page {
    public readonly instanceSymbols = new Set<SymbolHandle>()
    public readonly staticSymbols = new Set<SymbolHandle>()

    constructor(
        public readonly rootSymbol: SymbolHandle,
        public readonly rootPrototypeSymbol: SymbolHandle | null,
    ) { }
}

export class MarkdownPageBuilder {
    protected readonly _result: string[] = []

    public makeAnchoredText(text: string) {
        return `[${text}]{id="${this.owner.symbolNameToAnchor(text)}"}`
    }

    public add(line: string) {
        this._result.push(line)
    }

    public addHeading(text: string) {
        this._result.push("\n# " + text + "\n")
    }

    public addSubheading(text: string) {
        this._result.push("\n## " + text + "\n")
    }

    public addSymbolHeading(symbol: SymbolHandle) {
        if (this.owner.rootSymbols.has(symbol)) {
            this._result.push(`### <code>${this.makeAnchoredText(this.tryMakeLink(symbol))}</code>`)
        } else {
            this._result.push(`### \`${this.makeAnchoredText(symbol.name)}\``)
        }
    }

    public addSymbolInfo(symbol: SymbolHandle) {
        const shortName = symbol.getShortName()
        if (symbol.overloads) {
            this._result.push("**Overloads:**")

            for (const overload of symbol.overloads) {
                const formattedParameters: string[] = overload.parameters.map((v, i) => `${v}: ${this.tryMakeLink(overload.types?.at(i) ?? this.owner.db.getSymbol("any"))}`)

                if (overload.isVariadic) {
                    formattedParameters.push("...")
                }

                this._result.push(`  - <code>${shortName}(${formattedParameters.join(", ")})</code>`)
            }

            this._result.push("")
        }

        if (symbol.value) {
            this._result.push(`**Type:** <code>${this.tryMakeLink(symbol.value)}</code>`)
            this._result.push("")
        }

        if (symbol.summary) {
            const summary = symbol.summary.join("\n")
                .replace(/\{@link\s?([\w.]+)\}/g, (_, name) => {
                    const symbol = this.owner.db.tryGetSymbol(name)
                    if (!symbol) return `\`${name}\``
                    return `<code>${this.tryMakeLink(symbol)}</code>`
                })
            this._result.push(summary + "\n")
        }

        this._result.push("<hr/>")
    }

    public addSymbol(symbol: SymbolHandle) {
        this.addSymbolHeading(symbol)

        if (!this.owner.rootSymbols.has(symbol)) {
            this.addSites(symbol.sites)
        }

        this.addSymbolInfo(symbol)
    }

    public addSites(sites: string[]) {
        this._result.push("<sub>" + sites.map(site => relative(this.owner.project.path, site)).join("\\\n") + "</sub>\n")
    }

    public tryMakeLink(symbol: SymbolHandle) {
        const link = this.owner.findLinkToSymbol(symbol, this.extension)
        if (link) return `[${symbol.name}](${link})`
        return `${symbol.name}`
    }

    public build() {
        return this._result.join("\n") + "\n"
    }

    constructor(
        public readonly title: string,
        public readonly owner: DocumentationBuilder,
        public readonly extension: string,
        public readonly filename: string,
    ) { }
}

export class DocumentationBuilder {
    public readonly globalPage = new Page(this.db.globalScope, null)
    public readonly pages: Page[] = [this.globalPage]
    public readonly printedSymbols = new Map<SymbolHandle, Set<Page>>()
    public readonly rootSymbols = new Set<SymbolHandle>()

    public markPrintedSymbol(symbol: SymbolHandle, page: Page) {
        ensureKey(this.printedSymbols, symbol, () => new Set()).add(page)
    }

    public sortSymbols() {
        const symbols = Array.from(this.db.getSymbolNames())

        for (const symbolName of symbols) {
            const symbol = this.db.getSymbol(symbolName)

            if (!symbol.isEntry) continue

            const prototype = this.db.tryGetSymbol(symbolName + ".prototype")
            const isRoot = prototype != null || (isWord(symbolName, 0) && symbolName[0] == symbolName[0].toUpperCase())
            if (isRoot) {
                const page = new Page(symbol, prototype)
                this.pages.push(page)
                this.rootSymbols.add(symbol)
                this.markPrintedSymbol(symbol, page)

                for (const child of symbol.children) {
                    if (child == prototype) continue
                    page.staticSymbols.add(child)
                }

                if (prototype) {
                    this.markPrintedSymbol(prototype, page)
                    for (const child of prototype.children) {
                        page.instanceSymbols.add(child)
                    }

                    symbol.summary.push(...prototype.summary)
                    prototype.summary.length = 0
                }
            }

            this.globalPage.staticSymbols.add(symbol)
            if (!isRoot) this.markPrintedSymbol(symbol, this.globalPage)
        }
    }

    public symbolNameToAnchor(name: string) {
        if (name == "null") name = "symbol null"
        return name.toLowerCase().replace(/[^\w .-]/g, "").replace(/[ .]/g, "-").replace(/-{2,}/g, "-")
    }

    public findLinkToSymbol(symbol: SymbolHandle, extension: string) {
        const prints = this.printedSymbols.get(symbol)
        if (prints == null) return null

        const page = iteratorNth(prints)
        const symbolHeading = this.symbolNameToAnchor(symbol.name)
        return this.getFilenameForPage(page, extension) + "#" + symbolHeading
    }

    public getFilenameForPage(page: Page, extension: string) {
        return page == this.globalPage ? "index" + extension : page.rootSymbol.name + extension
    }

    public *buildMarkdown(extension = ".md") {
        for (const page of this.pages) {
            const builder = new MarkdownPageBuilder(
                (page == this.globalPage ? "Reference" : page.rootSymbol.name) + " - " + this.project.title,
                this, extension, this.getFilenameForPage(page, extension),
            )

            const inserts = this.project.getInsertsForFilename(builder.filename, ".md")

            if (page == this.globalPage) {
                builder.add(inserts.join("\n"))
                builder.addHeading(builder.makeAnchoredText("Reference"))
            } else {
                builder.add("[Back](index.html)")
                builder.addHeading(builder.makeAnchoredText(page.rootSymbol.name))

                if (page.rootPrototypeSymbol) {
                    builder.addSites(page.rootSymbol.sites.concat(page.rootPrototypeSymbol.sites))
                } else {
                    builder.addSites(page.rootSymbol.sites)
                }

                builder.addSymbolInfo(page.rootSymbol)
                builder.add(inserts.join("\n"))

                if (page.rootPrototypeSymbol) builder.addSymbolInfo(page.rootPrototypeSymbol)
            }

            if (page.staticSymbols.size > 0) {
                if (page != this.globalPage) builder.addSubheading("Static Properties")
                for (const symbol of page.staticSymbols) {
                    builder.addSymbol(symbol)
                }
            }

            if (page.instanceSymbols.size > 0) {
                if (page != this.globalPage) builder.addSubheading("Instance Properties")
                for (const symbol of page.instanceSymbols) {
                    builder.addSymbol(symbol)
                }
            }

            yield builder
        }
    }

    public *buildHtml(extension = ".html") {
        const renderer = new MmlHtmlRenderer()

        for (const markdown of this.buildMarkdown(extension)) {

            const parser = new MmlParser(markdown.build(), {})

            const root = parser.parseDocument()

            const inserts = this.project.getInsertsForFilename(markdown.filename, ".html")

            const html = inserts.join("\n") + "\n" + renderer.render(root)

            const output = templateHtml
                .replace(/{{BODY}}/, html)
                .replace(/{{TITLE}}/, markdown.title)

            yield { filename: markdown.filename, html: output }
        }
    }

    constructor(
        public readonly project: Project,
        public readonly db: SymbolDatabase,
    ) { }
}
