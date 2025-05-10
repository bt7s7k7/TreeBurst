import { isWord } from "../comTypes/util"
import { Diagnostic } from "../primitiveParser/Diagnostic"
import { NUMBER_PRIMITIVE } from "../primitiveParser/NumberPrimitive"
import { PredicatePrimitive } from "../primitiveParser/PredicatePrimitive"
import { Primitive, SKIP } from "../primitiveParser/Primitive"
import { PrimitiveParser } from "../primitiveParser/PrimitiveParser"
import { STRING_PRIMITIVE } from "../primitiveParser/StringPrimitive"
import { TreeNode } from "./TreeNode"

export class TreeBurstParser extends PrimitiveParser {
    public parse() {
        const result = this.parsePrimitive(TreeBurstParser.NODE)

        if (result == SKIP) {
            if (this.isDone()) {
                return TreeNode.withValue(null)
            }

            this.unexpectedToken()
            return null
        }

        this.skipWhitespace()
        if (!this.isDone()) {
            this.unexpectedToken()
        }

        return result
    }
}

export namespace TreeBurstParser {
    export const WORD = new PredicatePrimitive((v, i) => v[i] == "." || v[i] == "/" || isWord(v, i))

    export const NODE_CHILD = Primitive.create(parser => {
        const start = parser.index
        let name = parser.parsePrimitive(WORD)

        if (name != SKIP) {
            if (!parser.consume(":")) {
                parser.index = start
                name = SKIP
            }
        }

        parser.skipWhitespace()

        const child = parser.parsePrimitive(NODE)
        if (child == SKIP) {
            if (name != SKIP) {
                parser.unexpectedToken()
                return { name, child: TreeNode.default() }
            }

            return SKIP
        }

        return { name: name == SKIP ? null : name, child }
    })

    export const NODE_VALUE = [NUMBER_PRIMITIVE, STRING_PRIMITIVE, WORD]
    export const NODE = Primitive.create(parser => {

        const modifier = parser.consume(["#", "!", "$"])

        let value = parser.parsePrimitives(NODE_VALUE)

        const node = TreeNode.withValue(value == SKIP ? null : value)
        node.position = parser.getTokenPosition()

        if (modifier == "#") {
            if (value == SKIP) {
                parser.unexpectedToken()
            }

            const container = TreeNode.default()
            container.addChild(node)
            container.position = node.position
            return container
        }

        parser.skipWhitespace()
        let start
        if ((start = parser.consume(["{", "("]))) {
            for (const { name, child } of parser.parsePrimitivesUntil(start == "{" ? "}" : ")", [NODE_CHILD])) {
                if (name == null) {
                    node.addChild(child)
                } else {
                    node.setEntry(name, child)
                }

                parser.skipWhitespace()
                parser.consume(",")
            }
        } else {
            if (value == SKIP) {
                return SKIP
            }
        }

        if (modifier == "!") {
            if (node.value == null) {
                node.setValue(".x")
                return node
            } else {
                const container = TreeNode.withValue(".x")
                container.position = node.position
                container.addChild(node)
                return container
            }
        }

        if (modifier == "$") {
            if (node.value == null) {
                parser.addDiagnostic(new Diagnostic("Missing variable name", node.position))
                return node
            }

            if (node.entries != null || (node.children != null && node.children.length != 1)) {
                parser.addDiagnostic(new Diagnostic("Invalid variable usage", node.position))
                return node
            }

            const variableOperation = TreeNode.default()
            const container = TreeNode.default()

            variableOperation.position = node.position
            container.position = node.position
            variableOperation.addChild(container)
            container.addChild(node)

            if (node.children) {
                variableOperation.setValue(".st")
                const value = node.children[0]
                node.deleteEntry(0)
                variableOperation.addChild(value)
                return variableOperation
            } else {
                variableOperation.setValue(".ld")
                return variableOperation
            }
        }

        return node
    })
}
