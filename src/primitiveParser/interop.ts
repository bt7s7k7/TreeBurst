import { DescriptionFormatter } from "../prettyPrint/DescriptionFormatter"
import { inspect } from "../prettyPrint/inspect"
import { Formatter } from "../textFormat/Formatter"
import { HTMLFormatter } from "../textFormatHTML/HTMLFormatter"
import { Diagnostic } from "./Diagnostic"
import { Position } from "./Position"

export function registerParserChromeDevtoolsFormatters({ skipFilename = false } = {}) {
    if (!globalThis["window"]) return
    // @ts-ignore
    window.devtoolsFormatters ??= []
    // @ts-ignore
    const index = window.devtoolsFormatters.findIndex(v => v.kind == "pratt-formatter")
    // @ts-ignore
    if (index != -1) window.devtoolsFormatters.splice(index, 1)
    // @ts-ignore
    window.devtoolsFormatters.push({
        kind: "pratt-formatter",
        header(obj: unknown) {
            if (obj instanceof Position) {
                const format = obj.format()
                const start = skipFilename ? format.indexOf("\n") + 1 : 0
                return ["div", { style: "color: #57a7fd" }, format.slice(start)]
            }
            return null
        },
        hasBody() {
            return false
        },
    })
}

export function diagnosticToHtml(diagnostic: Diagnostic) {
    return HTMLFormatter.render(diagnostic.format({ error: true, colors: true }))
}

const _DEFAULT_COLOR: DescriptionFormatter.FormatOptions["color"] = (text, color) => Formatter.adapt(text, color.custom ? "white" : color.name)
export function inspectToHtml(object: any) {
    return HTMLFormatter.render(inspect(object, { color: _DEFAULT_COLOR }))
}
