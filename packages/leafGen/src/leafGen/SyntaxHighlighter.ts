import { unreachable } from "../comTypes/util"
import { Struct } from "../struct/Struct"
import { Type } from "../struct/Type"

export class ColorTheme extends Struct.define("ColorTheme", {
    colors: Type.string.as(Type.map),
    styles: Type.string.as(Type.map),
}) {
    protected _css: string | null = null
    public generateCSS() {
        return this._css ??= [...this.colors].map(([name, color]) => `.token-${name} { color: ${color}; }`).join("\n")
    }

    public findStyle(scope: string | null) {
        if (scope == null) return null
        const style = this.styles.get(scope)
        if (style == null) return null
        return `token-${style}`
    }
}

export class Pattern extends Struct.define("Pattern", class {
    public readonly name = Struct.field(Type.string.as(Type.nullable))
    public readonly match = Struct.field(Type.string.as(Type.nullable))
    public readonly include = Struct.field(Type.string.as(Type.nullable))
    public readonly begin = Struct.field(Type.string.as(Type.nullable))
    public readonly end = Struct.field(Type.string.as(Type.nullable))

    public readonly beginCaptures: Map<string, Pattern> | null = Struct.field(Captures_t.as(Type.nullable))
    public readonly endCaptures: Map<string, Pattern> | null = Struct.field(Captures_t.as(Type.nullable))
    public readonly captures: Map<string, Pattern> | null = Struct.field(Captures_t.as(Type.nullable))

    public readonly patterns: Pattern[] | null = Struct.field(Pattern.ref().as(Type.array).as(Type.nullable))
}) {
    protected _match: RegExp | null = null
    public getMatch() {
        return this._match ??= new RegExp(this.match ?? unreachable(), "yd")
    }

    protected _begin: RegExp | null = null
    public getBegin() {
        return this._begin ??= new RegExp(this.begin ?? unreachable(), "yd")
    }

    protected _end: RegExp | null = null
    public getEnd() {
        return this._end ??= new RegExp(this.end ?? unreachable(), "yd")
    }
}

export const Captures_t = Pattern.ref().as(Type.map)

export class LanguageDefinition extends Struct.define("LanguageDefinition", {
    patterns: Pattern.ref().as(Type.array),
    repository: Pattern.ref().as(Type.map),
}) { }

export type _HighlighterScope = Pattern & { patterns: Pattern[] }
export type HighlightedToken = { style: string | null, text: string }
export class SyntaxHighlighter {
    protected readonly _stack: _HighlighterScope[] = []
    protected readonly _names: string[] = []
    protected _index = 0

    public getApplicableScopeName() {
        return this._names.at(-1) ?? null
    }

    public pushScope(pattern: _HighlighterScope) {
        this._stack.push(pattern)
        if (pattern.name != null) this._names.push(pattern.name)
    }

    public popScope() {
        if (this._stack.length == 0) unreachable()

        const pattern = this._stack.pop()!
        if (pattern.name != null) {
            this._names.pop()
        }
    }

    public *getPatterns(patterns: Pattern[] | null = null): Generator<Pattern> {
        patterns ??= this._stack.at(-1)?.patterns ?? this.language.patterns
        if (patterns == null) {
            const currentScope = this._stack.at(-1) ?? unreachable()
            patterns = currentScope.patterns
        }

        for (const pattern of patterns) {
            if (pattern.include != null) {
                // The include uses something like JSON path. TreeBurst only uses
                // "#" + <repository name> syntax, so thats the only thing we support.
                const includedName = pattern.include.slice(1)
                const definition = this.language.repository.get(includedName)
                if (definition == null) throw new Error("Cannot find pattern '" + pattern.include + "' in repository")

                if (definition.begin != null || definition.match != null) yield definition
                else if (definition.patterns != null) yield* this.getPatterns(definition.patterns)

                // Having both include and content in a patter is probably not legal, but in any
                // case, TreeBurst doesn't use it, so don't support it.
                continue
            }

            if (pattern.begin != null || pattern.match != null) yield pattern
            else if (pattern.patterns != null) yield* this.getPatterns(pattern.patterns)
        }
    }

    public useRegexp(regexp: RegExp) {
        regexp.lastIndex = this._index
        const match = regexp.exec(this._input)

        if (match) {
            return { match, regexp }
        }

        return null
    }

    public *getTokensInMatch(match: RegExpMatchArray, name: string | null, captures: Map<string, Pattern> | null): Generator<HighlightedToken> {
        if (captures) {
            const groupCount = match.indices!.length
            let lastIndex = this._index

            for (let i = 0; i < groupCount; i++) {
                const capture = captures.get((i + 1).toString())
                if (capture == null) continue

                let [start, end] = match.indices![i + 1]
                const unmatched = this._input.slice(lastIndex, start)
                if (unmatched.length > 0) yield { style: this.theme.findStyle(name), text: unmatched }

                const content = match[i + 1]
                yield { style: this.theme.findStyle(capture.name), text: content }

                lastIndex = end
            }

            const totalEnd = match.index! + match[0].length
            if (lastIndex < totalEnd) {
                const unmatched = this._input.slice(lastIndex, totalEnd)
                yield { style: this.theme.findStyle(name), text: unmatched }
            }

            return
        }

        yield { style: this.theme.findStyle(name), text: match[0] }
    }

    public *getTokens(): Generator<HighlightedToken> {
        let unmatched = ""

        while (this._index < this._input.length) {
            let match: { pattern: Pattern, match: RegExpMatchArray, regexp: RegExp, isEnd: boolean } | null = null

            const currentScope = this._stack.at(-1)
            if (currentScope?.end != null) {
                const hit = this.useRegexp(currentScope.getEnd())

                if (hit) {
                    match = { ...hit, pattern: currentScope, isEnd: true }
                }
            }

            if (match == null) for (const pattern of this.getPatterns()) {
                if (pattern.match) {
                    const hit = this.useRegexp(pattern.getMatch())

                    if (hit) {
                        match = { ...hit, pattern, isEnd: false }
                        break
                    }
                }

                if (pattern.begin) {
                    const hit = this.useRegexp(pattern.getBegin())

                    if (hit) {
                        match = { ...hit, pattern, isEnd: false }
                        break
                    }
                }
            }

            if (match == null) {
                unmatched += this._input[this._index++]
                continue
            }

            if (unmatched.length > 0) {
                yield { style: this.theme.findStyle(this.getApplicableScopeName()), text: unmatched }
                unmatched = ""
            }

            if (match.isEnd) {
                yield* this.getTokensInMatch(match.match, match.pattern.name, match.pattern.endCaptures)
                this.popScope()
            } else if (match.pattern.begin != null) {
                yield* this.getTokensInMatch(match.match, match.pattern.name, match.pattern.beginCaptures)
                if (match.pattern.patterns == null) throw new Error("Scope does not have a pattern array")
                this.pushScope(match.pattern as _HighlighterScope)
            } else if (match.pattern.match != null) {
                yield { style: this.theme.findStyle(match.pattern.name), text: match.match[0] }
            } else {
                // If a match is found, the pattern must have either begin or match fields
                unreachable()
            }

            this._index = match.regexp.lastIndex
        }

        if (unmatched.length > 0) {
            yield { style: this.theme.findStyle(this.getApplicableScopeName()), text: unmatched }
        }
    }

    constructor(
        protected _input = "",
        public readonly language: LanguageDefinition,
        public readonly theme: ColorTheme,
    ) { }
}
