import { defineComponent, ref, shallowRef } from "vue"
import testFile from "../../../../test/standard.tb?raw"
import { evaluateExpression } from "../treeBurst/runtime/evaluateExpression"
import { ExpressionResult } from "../treeBurst/runtime/ExpressionResult"
import { GlobalScope, INTRINSIC } from "../treeBurst/runtime/GlobalScope"
import { Diagnostic } from "../treeBurst/support/Diagnostic"
import { InputDocument } from "../treeBurst/support/InputDocument"
import { TreeBurstParser } from "../treeBurst/syntax/TreeBurstParser"
import { Button } from "../vue3gui/Button"
import { initTestFunctions } from "./testFunctions"
import { inspectToHtml } from "./TreeBurstLanguage"

class _Test {
    public expectFail = false
    public errors: Diagnostic[] = []
    public get failed() { return (this.errors.length > 0) != this.expectFail }

    public evaluate() {
        const parser = new TreeBurstParser(this.document)
        const root = parser.parse()

        this.errors = parser.diagnostics

        if (this.errors.length > 0) {
            return
        }

        const result = new ExpressionResult()
        const scope = new GlobalScope()

        initTestFunctions(scope)
        evaluateExpression(root, scope, result)

        if (result.label != null) {
            if (result.value instanceof Diagnostic) {
                this.errors.push(result.value)
            } else {
                this.errors.push(new Diagnostic(`Unexpected termination with label "${result.label}"`, INTRINSIC))
            }
        }
    }

    constructor(
        public readonly document: InputDocument,
    ) { }

    public static parseTests(testFile: string) {
        const rootDocument = new InputDocument("anon", testFile)
        const tests: _Test[] = []
        const matches = [...testFile.matchAll(/\/\/ Test: ([^,\n]+)(?:, ([^\n]+))?/g)]
        for (let i = 0; i < matches.length; i++) {
            const match = matches[i]
            const nextMatch = matches.at(i + 1)

            const textStart = match.index! + match[0].length + 1
            const textEnd = nextMatch ? nextMatch.index : testFile.length
            const startLine = rootDocument.getCursorAtIndex(textStart).line

            const test = new _Test(new InputDocument(match[1], testFile.slice(textStart, textEnd), startLine))
            if (match[2] == "expect fail") {
                test.expectFail = true
            }

            tests.push(test)
        }

        return tests
    }
}

export const TestPage = (defineComponent({
    name: "TestPage",
    setup(props, ctx) {
        const tests = shallowRef<_Test[]>(null!)
        const fails = ref(0)

        function run() {
            tests.value = _Test.parseTests(testFile)
            fails.value = 0

            for (const test of tests.value) {
                test.evaluate()
                if (test.failed) fails.value++
            }
        }

        run()

        return () => (
            <div class="flex column as-page p-4">
                <div class="flex row justify-main center-cross mb-4">
                    {fails.value > 0 ? (
                        <div class="text-danger monospace">{fails.value} test failures</div>
                    ) : (
                        <div class="text-success monospace">All tests passed</div>
                    )}
                    <Button label="Rerun" variant="success" onClick={run} />
                </div>

                {tests.value.map(test => (<>
                    <Button to={{ name: "Editor", query: { code: test.document.content } }} clear class="flex row monospace text-left">
                        {test.failed ? (
                            <div class="w-100 text-danger">FAIL</div>
                        ) : (
                            <div class="w-100 text-success">SUCCESS</div>
                        )}

                        {test.document.path}
                    </Button>
                    {test.failed && test.errors.length > 0 && test.errors.map(error => (
                        <div class="pre-wrap monospace" innerHTML={inspectToHtml(error)}></div>
                    ))}
                </>))}
            </div>
        )
    },
}))
