import { readFileSync } from "node:fs"
import { normalize, relative, resolve } from "node:path"
import syntaxFile from "../../../../extension/syntaxes/tb.tmLanguage.json"
import templateHtml from "../../template.html"
import { createSortFunction, ensureKey, isAlpha, iteratorNth, Predicate, unreachable } from "../comTypes/util"
import { MmlHtmlRenderer } from "../miniML/MmlHtmlRenderer"
import { MmlParser } from "../miniML/MmlParser"
import { MmlWidget } from "../miniML/MmlWidget"
import { SyntaxNode } from "../miniML/SyntaxNode"
import { Struct } from "../struct/Struct"
import { Type } from "../struct/Type"
import { Project } from "./Project"
import { SymbolDatabase } from "./SymbolDatabase"
import { SymbolHandle } from "./SymbolHandle"
import { ColorTheme, LanguageDefinition, SyntaxHighlighter } from "./SyntaxHighlighter"

export class Page {
    public readonly instanceSymbols: SymbolHandle[] = []
    public readonly staticSymbols: SymbolHandle[] = []

    public getSuperclassPage() {
        if (this.rootPrototypeSymbol == null) return null
        const prototype = this.rootPrototypeSymbol.prototype
        if (prototype == null) return null
        const page = this.owner.rootSymbols.get(prototype)
        return page ?? null
    }

    constructor(
        public readonly owner: DocumentationBuilder,
        public readonly rootSymbol: SymbolHandle,
        public readonly rootPrototypeSymbol: SymbolHandle | null,
    ) { }
}

const _PENDING = Symbol.for("leafGen.include.pending")

export abstract class IncludeWidget extends Struct.define("Include", {
    path: Type.string,
}, MmlWidget) {
    public abstract builder: MarkdownPageBuilder

    public getValue(parser: MmlParser, content_1: SyntaxNode[]): SyntaxNode.Inline | null {
        const path = parser.path ?? unreachable()
        const targetPath = normalize(resolve(path, this.path))
        const existing = this.builder.owner.includeCache.get(targetPath)

        if (existing == _PENDING) {
            throw new Error("Detected circular reference while including " + targetPath)
        } else if (existing) {
            return existing
        }

        this.builder.owner.includeCache.set(targetPath, _PENDING)

        const content = this.builder.resolveLinkMacros(readFileSync(targetPath, "utf-8").replace(/\t/g, "    "))
        const parsed = new MmlParser(content, { path: targetPath, widgets: [this.constructor as any] }).parseDocument()

        const node = new SyntaxNode.Span({ content: [parsed] })
        this.builder.owner.includeCache.set(targetPath, node)
        return node
    }
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
                let parameters = overload.parameters
                if (parameters.at(-1) == "@" && overload.types?.at(-1)?.name == "BytecodeEmitter") {
                    // For macros, we don't need to write this parameter, since it applies to all
                    // macros so it gives no information and only takes up space.
                    parameters = parameters.slice(0, -1)
                }

                const formattedParameters = parameters.map((name, index) => {
                    const formattedName = `<span class="token-variable">${name}</span>`
                    const type = overload.types?.at(index) ?? this.owner.db.getSymbol("any")

                    return `${formattedName}: ${this.tryMakeLink(type)}`
                })

                if (overload.isVariadic) {
                    formattedParameters.push("...")
                }

                this._result.push(`  - <code>`
                    + `<span class="${shortName.startsWith("@") ? "token-keyword" : "token-function"}">${shortName}</span>`
                    + `(${formattedParameters.join(", ")})`
                    + `</code>`)
            }

            this._result.push("")
        }

        if (symbol.value) {
            this._result.push(`**Type:** <code>${this.tryMakeLink(symbol.value)}</code>`)
            this._result.push("")
        }

        if (symbol.summary) {
            const summary = this.resolveLinkMacros(symbol.summary.join("\n"))

            this._result.push(summary + "\n")
        }

        this._result.push("<hr/>")
    }

    public addSymbol(symbol: SymbolHandle, from: SymbolHandle | null) {
        this.addSymbolHeading(symbol)

        if (!this.owner.rootSymbols.has(symbol)) {
            this.addSites(symbol.sites)
        }

        if (from != null) {
            this.add(`**Inherited from:** <code>${this.tryMakeLink(from)}</code>`)
            this._result.push("")
        }

        this.addSymbolInfo(symbol)
    }

    public addSites(sites: string[]) {
        this._result.push("<sub>" + sites.map(site => relative(this.owner.project.path, site)).join("\\\n") + "</sub>\n")
    }

    public resolveLinkMacros(source: string) {
        return source.replace(/\{@link\s?([\w.]+)\}/g, (_, name) => {
            const symbol = this.owner.db.tryGetSymbol(name)
            if (!symbol) return `\`${name}\``
            return `<code>${this.tryMakeLink(symbol)}</code>`
        })
    }

    public tryMakeLink(symbol: SymbolHandle) {
        const link = this.owner.findLinkToSymbol(symbol, this.extension)
        if (link) return `[${symbol.name}](${link})`
        return `<span class="token-type">${symbol.name}</span>`
    }

    public build() {
        return this._result.join("\n") + "\n"
    }

    public makeIncludeWidget() {
        const self = this

        return class extends IncludeWidget {
            public override builder: MarkdownPageBuilder = self
        }
    }

    constructor(
        public readonly title: string,
        public readonly owner: DocumentationBuilder,
        public readonly extension: string,
        public readonly filename: string,
    ) { }
}

let _languageDefinition: LanguageDefinition | null = null
function _getLanguageDefinition() {
    return _languageDefinition ?? LanguageDefinition.deserialize(syntaxFile)
}

const _colorTheme = new ColorTheme({
    colors: new Map(Object.entries({
        "comment": "#247d01",
        "const": "#01258f",
        "function": "#7d0303",
        "keyword": "#995d00",
        "number": "#d90156",
        "string": "#3f3f3f",
        "type": "#016d41",
        "variable": "#101000",
        "property": "#007a81",
    } as Record<string, string>)),
    styles: new Map(Object.entries({
        "comment.block.tree-burst": "comment",
        "comment.line.double-slash.tree-burst": "comment",
        "constant.character.escape.tree-burst": "string",
        "constant.language.tree-burst": "const",
        "constant.numeric.decimal.tree-burst": "number",
        "entity.name.function.tree-burst": "function",
        // "entity.name.label.tree-burst": "other",
        "keyword.control.tree-burst": "keyword",
        // "meta.array-literal.tree-burst": "other",
        // "meta.function.tree-burst": "other",
        // "meta.group.tree-burst": "other",
        // "meta.objectliteral.tree-burst": "other",
        // "meta.template.expression.tree-burst": "other",
        "punctuation.definition.template-expression.begin.tree-burst": "const",
        "punctuation.definition.template-expression.end.tree-burst": "const",
        "storage.type.format.tree-burst": "keyword",
        "storage.type.function.tree-burst": "keyword",
        "string.quoted.double.tree-burst": "string",
        "string.quoted.other.tree-burst": "string",
        "string.quoted.single.tree-burst": "string",
        "support.class.tree-burst": "type",
        "support.type.property-name.implicit.tree-burst": "property",
        "support.type.property-name.tree-burst": "property",
        "variable.constant.tree-burst": "variable",
        "variable.other.tree-burst": "variable",
        "variable.parameter.tree-burst": "variable",
    } as Record<string, string>)),
})

export class DocumentationBuilder {
    public readonly globalPage = new Page(this, this.db.globalScope, null)
    public readonly pages: Page[] = [this.globalPage]
    public readonly printedSymbols = new Map<SymbolHandle, Set<Page>>()
    public readonly rootSymbols = new Map<SymbolHandle, Page>()
    public readonly includeCache = new Map<string, SyntaxNode.Inline | typeof _PENDING>()

    public markPrintedSymbol(symbol: SymbolHandle, page: Page) {
        ensureKey(this.printedSymbols, symbol, () => new Set()).add(page)
    }

    public sortSymbols() {
        const symbols = Array.from(this.db.getSymbolNames())

        for (const symbolName of symbols) {
            const symbol = this.db.getSymbol(symbolName)

            if (!symbol.isEntry) continue

            const prototype = this.db.tryGetSymbol(symbolName + ".prototype")
            const isRoot = prototype != null
                || (isAlpha(symbolName, 0) && symbolName[0] == symbolName[0].toUpperCase())
                || (symbolName[0] == "_" && isAlpha(symbolName, 1) && symbolName[1] == symbolName[1].toUpperCase())

            if (isRoot) {
                const page = new Page(this, symbol, prototype)
                this.pages.push(page)
                this.rootSymbols.set(symbol, page)
                this.markPrintedSymbol(symbol, page)

                for (const child of symbol.children) {
                    if (child == prototype) continue
                    page.staticSymbols.push(child)
                    this.markPrintedSymbol(child, page)
                }

                if (prototype) {
                    this.rootSymbols.set(prototype, page)
                    this.markPrintedSymbol(prototype, page)

                    for (const child of prototype.children) {
                        page.instanceSymbols.push(child)
                        this.markPrintedSymbol(child, page)
                    }

                    symbol.summary.push(...prototype.summary)
                    prototype.summary.length = 0
                }
            }

            this.globalPage.staticSymbols.push(symbol)
            if (!isRoot) this.markPrintedSymbol(symbol, this.globalPage)
        }

        for (const page of this.pages) {
            page.staticSymbols.sort(createSortFunction(v => v.name, "ascending"))
            page.instanceSymbols.sort(createSortFunction(v => v.name, "ascending"))
        }
    }

    public symbolNameToAnchor(name: string) {
        if (name == "null") name = "symbol null"
        return name.toLowerCase().replace(/[^\w .-]/g, "").replace(/[ .]/g, "-").replace(/-{2,}/g, "-")
    }

    public findLinkToSymbol(symbol: SymbolHandle, extension: string) {
        const prints = this.printedSymbols.get(symbol)
        if (prints == null) {
            const externalReference = this.project.findExternalReference(symbol.name)
            if (externalReference != null) return externalReference

            return null
        }

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
                builder.add(builder.resolveLinkMacros(inserts.join("\n")))
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
                builder.add(builder.resolveLinkMacros(inserts.join("\n")))

                if (page.rootPrototypeSymbol) builder.addSymbolInfo(page.rootPrototypeSymbol)
            }

            const emitHeadings = page != this.globalPage && page.staticSymbols.length > 0 && page.instanceSymbols.length > 0

            if (page.staticSymbols.length > 0) {
                if (emitHeadings) builder.addSubheading("Static Properties")
                for (const symbol of page.staticSymbols) {
                    builder.addSymbol(symbol, null)
                }
            }

            const overriddenNames = new Set<string>()

            if (page.instanceSymbols.length > 0) {
                if (emitHeadings) builder.addSubheading("Instance Properties")

                for (const symbol of page.instanceSymbols) {
                    builder.addSymbol(symbol, null)
                    overriddenNames.add(symbol.getShortName())
                }
            }

            const superSymbols: { symbol: SymbolHandle, from: SymbolHandle }[] = []
            for (let superPage = page.getSuperclassPage(); superPage != null; superPage = superPage.getSuperclassPage()) {
                superSymbols.push(...superPage.instanceSymbols.map(symbol => ({ symbol, from: superPage.rootSymbol })))
            }

            if (superSymbols.length > 0) {
                builder.addSubheading("Inherited Properties")
                for (const { symbol, from } of superSymbols) {
                    if (overriddenNames.has(symbol.getShortName())) continue

                    builder.addSymbol(symbol, from)
                    overriddenNames.add(symbol.getShortName())
                }
            }

            yield builder
        }
    }

    public *buildHtml(extension = ".html") {
        const renderer = new class extends MmlHtmlRenderer {
            protected _renderCodeBlock(node: SyntaxNode.CodeBlock): string {
                if (node.lang == null) return super._renderCodeBlock(node)

                const children: string[] = []

                const highlighter = new SyntaxHighlighter(node.content, _getLanguageDefinition(), _colorTheme)
                for (const token of highlighter.getTokens()) {
                    if (token.style != null) {
                        children.push(this._renderElementRaw("span", new Map([["class", token.style]]), this._renderText(token.text)))
                    } else {
                        children.push(this._renderText(token.text))
                    }
                }

                return this._renderElementRaw("pre", null, this._renderElementRaw("code", null, children.join("")))
            }
        }()

        for (const markdown of this.buildMarkdown(extension)) {
            const parser = new MmlParser(markdown.build().replace(/\t/g, "    "), {
                path: this.project.path,
                widgets: [markdown.makeIncludeWidget()],
            })

            const root = parser.parseDocument()

            const inserts = this.project.getInsertsForFilename(markdown.filename, ".html")

            let style = _colorTheme.generateCSS()

            const html = inserts.join("\n") + "\n" + renderer.render(root)

            const output = templateHtml
                .replace(/{{TITLE}}/, () => markdown.title)
                .replace(/\/\*STYLE\*\//, () => style)
                .replace(/{{BODY}}/, () => html)

            yield { filename: markdown.filename, content: output }
        }

        if (this.project.emitSymbolDatabase) {
            yield {
                filename: "symbols.json",
                content: JSON.stringify(Object.fromEntries([...this.printedSymbols].map(([symbol, pages]): [string, string] | null => {
                    const link = this.findLinkToSymbol(symbol, ".html")
                    if (link == null) return null

                    return [symbol.name, link]
                }).filter(Predicate.notNull()))),
            }
        }
    }

    constructor(
        public readonly project: Project,
        public readonly db: SymbolDatabase,
    ) { }
}
